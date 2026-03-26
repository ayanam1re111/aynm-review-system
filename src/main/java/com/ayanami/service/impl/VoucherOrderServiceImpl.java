package com.ayanami.service.impl;

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
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate  stringRedisTemplate;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        //3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        //4.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //创建锁对象(对用户加锁,并发效率更高，所以拼接上用户Id)
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        //获取锁
        boolean isLock = lock.tryLock(1200);
        //判断是否获取锁成功
        if (!isLock) {
            //失败
            return Result.fail("不允许重复下单！");

        }
        //获取代理对象
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally{
            //释放锁
            lock.unlock();
        }
    }

    @Transactional//操作两张表，加上回滚更安全
    public Result createVoucherOrder(Long voucherId) {
        //5.限定一人一单
        Long userId=UserHolder.getUser().getId();
        //5.1.查询订单
        long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //5.2.判断是否存在
        if(count>0){
            return Result.fail("您购买过此券，请勿重复购买");
        }
        //6.扣减库存
        boolean success = seckillVoucherService.update().setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)//where id = ? and stock > 0
                .update();
        if(!success){
            //扣减失败
            return Result.fail("库存不足");
        }
        //7.创建订单
        VoucherOrder voucherOrder=new VoucherOrder();
        //7.1.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //7.2.用户Id
        voucherOrder.setUserId(userId);
        //7.4.代金券Id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //8.返回订单id
        return Result.ok(orderId);}
    }

