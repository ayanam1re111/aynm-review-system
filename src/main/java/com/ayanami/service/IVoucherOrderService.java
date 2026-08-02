package com.ayanami.service;

import com.ayanami.dto.Result;
import com.ayanami.dto.VoucherOrderMessage;
import com.ayanami.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);

    /**
     * 处理订单消息：幂等校验 + 扣库存 + 建单，事务内完成。
     */
    void handleOrderMessage(VoucherOrderMessage message);

    /**
     * 查询订单处理状态。
     */
    Result queryOrderStatus(Long orderId);

    /**
     * 下单失败补偿：恢复 Redis 库存并标记订单失败。
     */
    void compensateOrderFailure(Long orderId, Long userId, Long voucherId);

}
