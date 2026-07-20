package com.hmdp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * RabbitMQ消息可靠性配置
 * 实现生产者确认和消息返回回调
 */
@Slf4j
@Configuration
public class RabbitMQConfirmConfig {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void init() {
        // 设置消息确认回调（消息是否到达交换机）
        rabbitTemplate.setConfirmCallback(new RabbitTemplate.ConfirmCallback() {
            @Override
            public void confirm(CorrelationData correlationData, boolean ack, String cause) {
                String messageId = correlationData != null ? correlationData.getId() : "unknown";
                if (ack) {
                    log.info("消息发送成功, messageId: {}", messageId);
                } else {
                    log.error("消息发送失败, messageId: {}, 原因: {}", messageId, cause);
                    // 此处可实现消息重发或死信队列处理
                }
            }
        });

        // 设置消息返回回调（消息到达交换机但未路由到队列）
        // Spring Boot 2.3.x 使用的是旧版 API
        rabbitTemplate.setReturnCallback(new RabbitTemplate.ReturnCallback() {
            @Override
            public void returnedMessage(Message message, int replyCode, String replyText,
                                       String exchange, String routingKey) {
                log.error("消息路由失败, 交换机: {}, 路由键: {}, 回复码: {}, 回复文本: {}",
                        exchange, routingKey, replyCode, replyText);
                // 此处可实现消息重发或死信队列处理
            }
        });
    }
}
