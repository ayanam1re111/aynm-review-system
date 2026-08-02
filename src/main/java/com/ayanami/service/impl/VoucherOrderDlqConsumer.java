package com.ayanami.service.impl;

import com.ayanami.dto.VoucherOrderMessage;
import com.ayanami.utils.MqConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 死信队列消费者：记录重试耗尽后进入死信队列的失败订单。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "hm.mq.enabled", havingValue = "true", matchIfMissing = true)
public class VoucherOrderDlqConsumer {

    @RabbitListener(queues = MqConstants.VOUCHER_ORDER_DLQ)
    public void onDlqMessage(VoucherOrderMessage message) {
        log.error("秒杀订单进入死信队列, orderId={}, userId={}, voucherId={}, createTime={}, 请人工核对处理",
                message.getOrderId(), message.getUserId(), message.getVoucherId(), message.getCreateTime());
    }
}
