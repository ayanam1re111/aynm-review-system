package com.ayanami.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.ayanami.dto.Result;
import com.ayanami.entity.SeckillVoucher;
import com.ayanami.entity.VoucherOrder;
import com.ayanami.mapper.VoucherOrderMapper;
import com.ayanami.service.ISeckillVoucherService;
import com.ayanami.service.IVoucherOrderService;
import com.ayanami.utils.RedisIdWorker;
import com.ayanami.utils.SimpleRedisLock;
import com.ayanami.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate  stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //单线程池（里面永远只有1个线程在干活）：异步处理队列中的订单
    private static final ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();

    //在当前类的对象实例化完成、依赖注入结束后，自动启动后台线程，因为当这个类初始化好了之后，随时都是有可能要执行的
    @PostConstruct
    private void init() {
        //提交一个任务给线程池，让其开启一个后台线程，一直运行
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());

    }
    private IVoucherOrderService proxy;
    /**
     * 秒杀优惠券入口
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //1.执行LUA脚本
        long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId));

        //2.判断结果是否为0
        int r = result.intValue();
        if (r != 0) {
            //2.1.不为0.代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        //3.获取代理对象（全局唯一）
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //4.返回订单id（不等待数据库执行）
        return Result.ok(orderId);
    }
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //获取用户
//        Long userId=UserHolder.getUser().getId();
//        //1.执行LUA脚本
//        Long result=stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(),userId.toString());
//
//        //2.判断结果是否为0
//        int r=result.intValue();
//        if(r!=0){
//            //2.1.不为0.代表没有购买资格
//            return Result.fail(r==1?"库存不足":"不能重复下单");}
//        //2.2.为0，有购买资格，把下单信息保存到阻塞队列
//        VoucherOrder voucherOrder=new VoucherOrder();
//        //2.3.订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //2.4.用户id
//        voucherOrder.setUserId(userId);
//        //2.5.代金券id
//        voucherOrder.setVoucherId(voucherId);
//        //2.6.放入阻塞队列
//        orderTasks.add(voucherOrder);
//        //3.获取代理对象（全局唯一）
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//        //4.返回订单id（不等待数据库执行）
//        return Result.ok(orderId);
//    }

    /**
     * 线程任务（死循环，一直盯着阻塞队列）
     */
    private class VoucherOrderHandler implements Runnable{
        String queueName="stream.orders";
    @Override
    public void run(){
        while(true){
            try{
                //1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                List<MapRecord<String,Object,Object>> list=stringRedisTemplate.opsForStream().read(
                  Consumer.from("g1","c1"),//传入组和消费者名称
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(queueName, ReadOffset.lastConsumed()));

                //2.判断消息获取是否成功
                if(list==null || list.isEmpty()) {
                    //2.1.如果获取失败，说明没有消息，继续下一次循环
                    continue;
                }
                //3.解析消息中的订单信息
                MapRecord<String, Object, Object> record = list.get(0);
                //String是消息的id，后面的两个object是key-value(eg,"userId",userId)
                Map<Object,Object> values= record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);//得到order对象

                //4.如果获取成功，可以下单
                handleVoucherOrder(voucherOrder);
                //4.ACK确认 SACK stream.orders g1 id
                stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());}
            catch(Exception e){
                log.error("订单处理异常",e);
                handlePendingList();

            }
        }
    }

        private void handlePendingList() {
            while(true){
                try{
                    //1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0,0读的是pendinglist
                    List<MapRecord<String,Object,Object>> list=stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1","c1"),//传入组和消费者名称
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.from("0")));

                    //2.判断消息获取是否成功
                    if(list==null || list.isEmpty()) {
                        //2.1.如果获取失败，说明没有异常消息，结束循环
                        break;
                    }
                    //3.解析消息中的异常订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    //String是消息的id，后面的两个object是key-value(eg,"userId",userId)
                    Map<Object,Object> values= record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);//得到order对象

                    //4.如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    //4.ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());}
                catch(Exception e){
                    log.error("pending-list订单处理异常",e);

                }
            }

        }
    }
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 1.获取用户
        Long userId = voucherOrder.getUserId();
        // 2.创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 3.尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 4.判断是否获得锁成功
        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试
            log.error("不允许重复下单！");
            return;
        }
        try {
            //注意：由于是spring的事务是放在threadLocal中，此时的是多线程，事务会失效
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }
    //阻塞队列，存待创建订单
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
    /**
     * 真正在数据库中创建订单
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
   }

