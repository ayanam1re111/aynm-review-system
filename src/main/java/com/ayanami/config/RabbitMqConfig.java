package com.ayanami.config;

import com.ayanami.utils.MqConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置：声明秒杀订单队列/交换机/死信队列，配置 JSON 消息转换器和 RabbitTemplate。
 * 通过 {@code hm.mq.enabled} 开关控制，未启用时不创建任何 MQ Bean。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "hm.mq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqConfig {

    @Resource
    private ConnectionFactory connectionFactory;

    @Bean
    public DirectExchange voucherOrderExchange() {
        return new DirectExchange(MqConstants.VOUCHER_ORDER_EXCHANGE, true, false);
    }

    @Bean
    public Queue voucherOrderQueue() {
        // 声明死信参数，重试耗尽的消息进死信队列
        Map<String, Object> args = new HashMap<>();
        args.put(MqConstants.DEAD_LETTER_EXCHANGE_KEY, MqConstants.VOUCHER_ORDER_DLX_EXCHANGE);
        args.put(MqConstants.DEAD_LETTER_ROUTING_KEY, MqConstants.VOUCHER_ORDER_DLX_ROUTING_KEY);
        return new Queue(MqConstants.VOUCHER_ORDER_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding voucherOrderBinding() {
        return BindingBuilder.bind(voucherOrderQueue())
                .to(voucherOrderExchange())
                .with(MqConstants.VOUCHER_ORDER_ROUTING_KEY);
    }

    @Bean
    public DirectExchange voucherOrderDlxExchange() {
        return new DirectExchange(MqConstants.VOUCHER_ORDER_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue voucherOrderDlq() {
        return new Queue(MqConstants.VOUCHER_ORDER_DLQ, true);
    }

    @Bean
    public Binding voucherOrderDlqBinding() {
        return BindingBuilder.bind(voucherOrderDlq())
                .to(voucherOrderDlxExchange())
                .with(MqConstants.VOUCHER_ORDER_DLX_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        // 开启 mandatory，消息无法路由时触发 Return 回调
        template.setMandatory(true);
        template.setReturnsCallback(returned ->
                log.error("MQ 消息路由失败, exchange={}, routingKey={}, replyText={}, message={}",
                        returned.getExchange(), returned.getRoutingKey(),
                        returned.getReplyText(), returned.getMessage()));
        // 发送确认回调，未确认时记录日志
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("MQ 消息发送确认失败, correlationData={}, cause={}", correlationData, cause);
            }
        });
        return template;
    }
}
