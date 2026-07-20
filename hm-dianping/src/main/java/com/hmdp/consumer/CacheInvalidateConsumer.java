package com.hmdp.consumer;

import com.hmdp.config.CacheInvalidateMQConfig;
import com.hmdp.config.ShopLocalCache;
import com.hmdp.utils.RedisConstants;
import com.hmdp.dto.CacheInvalidateMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 缓存失效消息消费者
 */
@Slf4j
@Component
public class CacheInvalidateConsumer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopLocalCache shopLocalCache;

    @RabbitListener(queues = CacheInvalidateMQConfig.CACHE_INVALIDATE_QUEUE)
    public void handleCacheInvalidate(CacheInvalidateMessage message) {
        Long shopId = message.getShopId();
        log.info("收到缓存失效消息，shopId: {}", shopId);

        try {
            invalidateCache(shopId);
            log.info("缓存失效处理成功，shopId: {}", shopId);
        } catch (Exception e) {
            log.error("缓存失效处理失败，shopId: {}, error: {}", shopId, e.getMessage());
            throw e;
        }
    }

    @RabbitListener(queues = CacheInvalidateMQConfig.CACHE_INVALIDATE_DEAD_QUEUE)
    public void handleDeadLetter(CacheInvalidateMessage message) {
        Long shopId = message.getShopId();
        log.error("缓存失效消息进入死信队列，shopId: {}, retryCount: {}", shopId, message.getRetryCount());

        try {
            invalidateCache(shopId);
            log.info("死信消息处理成功，shopId: {}", shopId);
        } catch (Exception e) {
            log.error("死信消息处理失败，shopId: {}, error: {}", shopId, e.getMessage());
        }
    }

    private void invalidateCache(Long shopId) {
        String key = RedisConstants.CACHE_SHOP_KEY + shopId;
        stringRedisTemplate.delete(key);
        shopLocalCache.invalidate(shopId);
    }
}
