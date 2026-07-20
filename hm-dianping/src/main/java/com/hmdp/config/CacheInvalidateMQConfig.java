package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存失效消息队列配置
 */
@Configuration
public class CacheInvalidateMQConfig {

    public static final String CACHE_INVALIDATE_QUEUE = "cache.invalidate.queue";
    public static final String CACHE_INVALIDATE_EXCHANGE = "cache.invalidate.exchange";
    public static final String CACHE_INVALIDATE_ROUTING_KEY = "cache.invalidate.routing.key";
    public static final String CACHE_INVALIDATE_DEAD_QUEUE = "cache.invalidate.dead.queue";
    public static final String CACHE_INVALIDATE_DEAD_ROUTING_KEY = "cache.invalidate.dead.routing.key";

    @Bean
    public Queue cacheInvalidateQueue() {
        return QueueBuilder.durable(CACHE_INVALIDATE_QUEUE)
                .withArgument("x-dead-letter-exchange", CACHE_INVALIDATE_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", CACHE_INVALIDATE_DEAD_ROUTING_KEY)
                .withArgument("x-message-ttl", 30000)
                .build();
    }

    @Bean
    public Queue cacheInvalidateDeadQueue() {
        return QueueBuilder.durable(CACHE_INVALIDATE_DEAD_QUEUE).build();
    }

    @Bean
    public DirectExchange cacheInvalidateExchange() {
        return new DirectExchange(CACHE_INVALIDATE_EXCHANGE, true, false);
    }

    @Bean
    public Binding cacheInvalidateBinding() {
        return BindingBuilder.bind(cacheInvalidateQueue())
                .to(cacheInvalidateExchange())
                .with(CACHE_INVALIDATE_ROUTING_KEY);
    }

    @Bean
    public Binding cacheInvalidateDeadBinding() {
        return BindingBuilder.bind(cacheInvalidateDeadQueue())
                .to(cacheInvalidateExchange())
                .with(CACHE_INVALIDATE_DEAD_ROUTING_KEY);
    }
}
