package com.ayanami.utils;

/**
 * RabbitMQ 交换机 / 队列 / 路由键常量。
 */
public class MqConstants {

    public static final String VOUCHER_ORDER_EXCHANGE = "voucher.order.exchange";
    public static final String VOUCHER_ORDER_QUEUE = "voucher.order.queue";
    public static final String VOUCHER_ORDER_ROUTING_KEY = "voucher.order.routingKey";

    public static final String VOUCHER_ORDER_DLX_EXCHANGE = "voucher.order.dlx.exchange";
    public static final String VOUCHER_ORDER_DLQ = "voucher.order.dlq";
    public static final String VOUCHER_ORDER_DLX_ROUTING_KEY = "voucher.order.dlx.routingKey";

    /** 死信参数名 */
    public static final String DEAD_LETTER_EXCHANGE_KEY = "x-dead-letter-exchange";
    public static final String DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";

    /** 订单处理状态 */
    public static final String ORDER_STATUS_PROCESSING = "PROCESSING";
    public static final String ORDER_STATUS_SUCCESS = "SUCCESS";
    public static final String ORDER_STATUS_FAILED = "FAILED";

    private MqConstants() {
    }
}
