package com.ayanami.service.impl;

import com.ayanami.dto.VoucherOrderMessage;
import com.ayanami.exception.OrderBusinessException;
import com.ayanami.service.IVoucherOrderService;
import com.ayanami.utils.MqConstants;
import com.ayanami.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;

/**
 * 秒杀订单消息消费者：接收订单消息并异步创建订单。
 * 业务异常直接 ack 不重试；其它异常交给 Spring Retry，耗尽后进死信队列。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "hm.mq.enabled", havingValue = "true", matchIfMissing = true)
public class VoucherOrderConsumer {

    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = MqConstants.VOUCHER_ORDER_QUEUE)
    public void onMessage(VoucherOrderMessage message) {
        try {
            voucherOrderService.handleOrderMessage(message);
            setOrderStatus(message.getOrderId(), MqConstants.ORDER_STATUS_SUCCESS);
        } catch (OrderBusinessException e) {
            // 业务失败：补偿 Redis 库存并标记失败
            log.warn("订单业务处理失败, orderId={}, userId={}, voucherId={}, reason={}",
                    message.getOrderId(), message.getUserId(), message.getVoucherId(), e.getMessage());
            voucherOrderService.compensateOrderFailure(message.getOrderId(), message.getUserId(), message.getVoucherId());
        }
    }

    /**
     * 更新订单处理状态。
     */
    private void setOrderStatus(Long orderId, String status) {
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisConstants.ORDER_STATUS_KEY + orderId,
                    status,
                    Duration.ofMinutes(RedisConstants.ORDER_STATUS_TTL));
        } catch (Exception e) {
            log.error("更新订单状态失败, orderId={}, status={}", orderId, status, e);
        }
    }
}
