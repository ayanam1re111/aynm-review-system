package com.ayanami.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀订单消息：发送到 RabbitMQ 的异步下单消息。
 */
@Data
public class VoucherOrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long orderId;

    /** 用户 ID */
    private Long userId;

    /** 秒杀优惠券 ID */
    private Long voucherId;

    /** 下单时间 */
    private LocalDateTime createTime;

    public VoucherOrderMessage() {
    }

    public VoucherOrderMessage(Long orderId, Long userId, Long voucherId, LocalDateTime createTime) {
        this.orderId = orderId;
        this.userId = userId;
        this.voucherId = voucherId;
        this.createTime = createTime;
    }
}
