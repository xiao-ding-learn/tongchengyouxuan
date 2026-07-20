package com.hmdp.task;

import com.hmdp.utils.RedisConstants;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 库存一致性检查定时任务
 * 定期核对Redis和数据库中的库存数据
 */
@Slf4j
@Component
public class StockConsistencyTask {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    /**
     * 每分钟检查一次库存一致性
     */
    @Scheduled(fixedDelay = 60000)
    public void checkStockConsistency() {
        log.debug("开始检查库存一致性...");
        
        try {
            // 查询所有秒杀券
            List<SeckillVoucher> vouchers = seckillVoucherService.list();
            
            for (SeckillVoucher voucher : vouchers) {
                Long voucherId = voucher.getVoucherId();
                Integer dbStock = voucher.getStock();
                
                // 从Redis获取库存
                String redisStockStr = stringRedisTemplate.opsForValue()
                        .get(RedisConstants.SECKILL_STOCK_KEY + voucherId);
                
                if (redisStockStr == null) {
                    // Redis中没有库存数据，可能是缓存过期，从数据库同步
                    log.warn("Redis库存数据缺失，voucherId: {}, 从数据库同步", voucherId);
                    stringRedisTemplate.opsForValue()
                            .set(RedisConstants.SECKILL_STOCK_KEY + voucherId, dbStock.toString());
                    continue;
                }
                
                int redisStock = Integer.parseInt(redisStockStr);
                
                // 比较库存
                if (redisStock != dbStock) {
                    log.error("库存不一致！voucherId: {}, Redis库存: {}, 数据库库存: {}", 
                            voucherId, redisStock, dbStock);
                    
                    // 以Redis为准更新数据库（因为Redis已扣减，数据库应该同步）
                    boolean success = seckillVoucherService.update()
                            .set("stock", redisStock)
                            .eq("voucher_id", voucherId)
                            .update();
                    
                    if (success) {
                        log.info("已修复库存不一致，voucherId: {}, 库存更新为: {}", voucherId, redisStock);
                    } else {
                        log.error("库存修复失败，voucherId: {}", voucherId);
                    }
                }
            }
            
            log.debug("库存一致性检查完成");
        } catch (Exception e) {
            log.error("库存一致性检查异常", e);
        }
    }
}
