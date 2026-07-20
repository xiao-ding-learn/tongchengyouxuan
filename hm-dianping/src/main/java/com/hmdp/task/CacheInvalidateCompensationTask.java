package com.hmdp.task;

import com.hmdp.config.ShopLocalCache;
import com.hmdp.utils.RedisConstants;
import com.hmdp.entity.CacheInvalidateRecord;
import com.hmdp.service.ICacheInvalidateRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 缓存失效补偿任务
 * 定期检查未成功的缓存失效记录并重试
 */
@Slf4j
@Component
public class CacheInvalidateCompensationTask {

    @Resource
    private ICacheInvalidateRecordService cacheInvalidateRecordService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopLocalCache shopLocalCache;

    @Resource
    private RabbitTemplate rabbitTemplate;

    private static final int MAX_RETRY_COUNT = 5;

    @Scheduled(fixedDelay = 60000)
    public void compensateFailedInvalidations() {
        log.debug("开始缓存失效补偿任务...");

        List<CacheInvalidateRecord> records = cacheInvalidateRecordService.lambdaQuery()
                .eq(CacheInvalidateRecord::getStatus, 0)
                .lt(CacheInvalidateRecord::getUpdateTime, LocalDateTime.now().minusMinutes(1))
                .list();

        for (CacheInvalidateRecord record : records) {
            if (record.getRetryCount() >= MAX_RETRY_COUNT) {
                log.error("缓存失效补偿失败，已达最大重试次数，shopId: {}", record.getShopId());
                record.setStatus(2);
                cacheInvalidateRecordService.updateById(record);
                continue;
            }

            try {
                invalidateCache(record.getShopId());

                record.setStatus(1);
                record.setUpdateTime(LocalDateTime.now());
                cacheInvalidateRecordService.updateById(record);

                log.info("缓存失效补偿成功，shopId: {}", record.getShopId());
            } catch (Exception e) {
                log.error("缓存失效补偿失败，shopId: {}, error: {}", record.getShopId(), e.getMessage());

                record.setRetryCount(record.getRetryCount() + 1);
                record.setUpdateTime(LocalDateTime.now());
                cacheInvalidateRecordService.updateById(record);
            }
        }

        log.debug("缓存失效补偿任务完成");
    }

    private void invalidateCache(Long shopId) {
        String key = RedisConstants.CACHE_SHOP_KEY + shopId;
        stringRedisTemplate.delete(key);
        shopLocalCache.invalidate(shopId);
    }
}
