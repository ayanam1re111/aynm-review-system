package com.ayanami.exception;

/**
 * 订单业务异常：表示不需要重试的业务失败，如库存不足、重复下单。
 */
public class OrderBusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OrderBusinessException(String message) {
        super(message);
    }

    public OrderBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
