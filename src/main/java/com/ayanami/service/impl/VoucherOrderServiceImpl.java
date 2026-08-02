package com.ayanami.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ayanami.dto.Result;
import com.ayanami.dto.VoucherOrderMessage;
import com.ayanami.entity.VoucherOrder;
import com.ayanami.exception.OrderBusinessException;
import com.ayanami.mapper.VoucherOrderMapper;
import com.ayanami.service.ISeckillVoucherService;
import com.ayanami.service.IVoucherOrderService;
import com.ayanami.service.UserBehaviorService;
import com.ayanami.utils.MqConstants;
import com.ayanami.utils.RedisConstants;
import com.ayanami.utils.RedisIdWorker;
import com.ayanami.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀订单服务：Lua 校验通过后发 RabbitMQ 异步下单。
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private UserBehaviorService userBehaviorService;
    /** RabbitMQ 模板，未启用 MQ 时为空 */
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> RECOVER_STOCK_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

        RECOVER_STOCK_SCRIPT = new DefaultRedisScript<>();
        RECOVER_STOCK_SCRIPT.setLocation(new ClassPathResource("recoverStock.lua"));
        RECOVER_STOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 秒杀优惠券：Lua 校验通过后发送订单消息到 RabbitMQ，立即返回订单 ID。
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // MQ 未启用时直接提示
        if (rabbitTemplate == null) {
            return Result.fail("秒杀功能未启用（RabbitMQ 未配置）");
        }
        // 获取用户并生成订单 ID
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        // 先标记订单为处理中
        stringRedisTemplate.opsForValue().set(
                RedisConstants.ORDER_STATUS_KEY + orderId,
                MqConstants.ORDER_STATUS_PROCESSING,
                Duration.ofMinutes(RedisConstants.ORDER_STATUS_TTL));

        // 执行 Lua：校验库存 + 一人一单 + 预扣库存
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId));
        int r = result.intValue();
        if (r != 0) {
            // 无购买资格，清理状态
            stringRedisTemplate.delete(RedisConstants.ORDER_STATUS_KEY + orderId);
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 发送订单消息到 RabbitMQ，等待 Broker 确认
        VoucherOrderMessage message = new VoucherOrderMessage(orderId, userId, voucherId, LocalDateTime.now());
        CorrelationData correlationData = new CorrelationData(String.valueOf(orderId));
        try {
            rabbitTemplate.convertAndSend(
                    MqConstants.VOUCHER_ORDER_EXCHANGE,
                    MqConstants.VOUCHER_ORDER_ROUTING_KEY,
                    message,
                    correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                // 发送失败：补偿 Redis 库存
                compensateOrderFailure(orderId, userId, voucherId);
                return Result.fail("下单失败，请重试");
            }
        } catch (Exception e) {
            log.error("MQ 发送秒杀订单消息失败, orderId={}", orderId, e);
            compensateOrderFailure(orderId, userId, voucherId);
            return Result.fail("下单失败，请重试");
        }

        // 记录下单行为
        userBehaviorService.recordOrder(userId, voucherId);

        return Result.ok(orderId);
    }

    /**
     * 下单失败补偿：恢复 Redis 预扣库存、移除一人一单标记，标记订单为失败。
     */
    @Override
    public void compensateOrderFailure(Long orderId, Long userId, Long voucherId) {
        try {
            stringRedisTemplate.execute(
                    RECOVER_STOCK_SCRIPT, Collections.emptyList(),
                    voucherId.toString(), userId.toString());
            stringRedisTemplate.opsForValue().set(
                    RedisConstants.ORDER_STATUS_KEY + orderId,
                    MqConstants.ORDER_STATUS_FAILED,
                    Duration.ofMinutes(RedisConstants.ORDER_STATUS_TTL));
        } catch (Exception e) {
            log.error("秒杀库存补偿失败, orderId={}", orderId, e);
        }
    }

    /**
     * 处理订单消息：幂等校验 + 扣库存 + 保存订单，事务内完成。
     */
    @Override
    @Transactional
    public void handleOrderMessage(VoucherOrderMessage message) {
        Long orderId = message.getOrderId();
        Long userId = message.getUserId();
        Long voucherId = message.getVoucherId();

        // 按订单 ID 幂等：已存在则跳过
        long byId = query().eq("id", orderId).count();
        if (byId > 0) {
            return;
        }

        // 同一用户加锁，避免并发重复建单
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        if (!lock.tryLock()) {
            return;
        }
        try {
            // 一人一单校验
            long byUserVoucher = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (byUserVoucher > 0) {
                return;
            }

            // 条件扣减库存，防止超卖
            boolean success = seckillVoucherService.update().setSql("stock=stock-1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!success) {
                throw new OrderBusinessException("库存不足");
            }

            // 保存订单
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setPayType(1);
            voucherOrder.setStatus(1);
            voucherOrder.setCreateTime(message.getCreateTime());
            try {
                save(voucherOrder);
            } catch (DuplicateKeyException e) {
                // 唯一索引兜底：订单已存在
                log.info("订单已存在，幂等跳过, orderId={}", orderId);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 查询订单处理状态。
     */
    @Override
    public Result queryOrderStatus(Long orderId) {
        String status = stringRedisTemplate.opsForValue().get(RedisConstants.ORDER_STATUS_KEY + orderId);
        if (StrUtil.isBlank(status)) {
            return Result.fail("订单不存在或状态已过期");
        }
        return Result.ok(status);
    }

    // 以下为原方案遗留方法，新流程已由 handleOrderMessage 替代
    /**
     * 真正在数据库中创建订单
     *   // 1.查是否已下单
     *     // 2.扣数据库库存
     *     // 3.写数据库订单
     * @param voucherOrder
     */
    @Transactional//操作两张表，加上回滚更安全
    //Transactional必须由代理对象调用才生效
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5.限定一人一单
        Long userId=voucherOrder.getUserId();
        //5.1.查询订单
        long count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //5.2.判断是否存在
        if(count>0){
            log.error("您购买过此券，请勿重复购买");
            return;
        }
        //6.扣减库存
        boolean success = seckillVoucherService.update().setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock",0)//where id = ? and stock > 0
                .update();
        if(!success){
            //扣减失败
            log.error("库存不足");
        }
        //7.创建订单
        save(voucherOrder);
    }

    // ==================== 原阻塞队列方案====================
//    private BlockingQueue<VoucherOrder> orderTasks=new ArrayBlockingQueue<>(1024*1024);
//    private class VoucherOrderHandler implements Runnable {
//        @Override
//        public void run() {
//            while(true){
//                //1.获取队列中的订单信息
//                try {
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    //2.真正创建订单
//                    proxy.createVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("订单处理异常",e);
//                }}
//        }
//    }
}
