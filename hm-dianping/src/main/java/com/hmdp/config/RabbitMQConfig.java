package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    // 订单队列名称
    public static final String ORDER_QUEUE = "order.queue";
    // 订单交换机名称
    public static final String ORDER_EXCHANGE = "order.exchange";
    // 订单路由键
    public static final String ORDER_ROUTING_KEY = "order.routing.key";
    
    // 死信队列名称
    public static final String ORDER_DEAD_QUEUE = "order.dead.queue";
    // 死信路由键
    public static final String ORDER_DEAD_ROUTING_KEY = "order.dead.routing.key";

    // 声明订单队列（带死信队列配置）
    @Bean
    public Queue orderQueue() {
        Map<String, Object> args = new HashMap<>();
        // 设置死信交换机
        args.put("x-dead-letter-exchange", ORDER_EXCHANGE);
        // 设置死信路由键
        args.put("x-dead-letter-routing-key", ORDER_DEAD_ROUTING_KEY);
        // 设置消息过期时间（60秒）
        args.put("x-message-ttl", 60000);
        return QueueBuilder.durable(ORDER_QUEUE).withArguments(args).build();
    }

    // 声明死信队列
    @Bean
    public Queue orderDeadQueue() {
        return QueueBuilder.durable(ORDER_DEAD_QUEUE).build();
    }

    // 声明交换机
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    // 绑定订单队列和交换机
    @Bean
    public Binding orderBinding() {
        return BindingBuilder.bind(orderQueue())
                .to(orderExchange())
                .with(ORDER_ROUTING_KEY);
    }

    // 绑定死信队列和交换机
    @Bean
    public Binding orderDeadBinding() {
        return BindingBuilder.bind(orderDeadQueue())
                .to(orderExchange())
                .with(ORDER_DEAD_ROUTING_KEY);
    }
}
