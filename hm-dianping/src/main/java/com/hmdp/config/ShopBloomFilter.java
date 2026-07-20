package com.hmdp.config;

import com.hmdp.mapper.ShopMapper;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;

@Component
public class ShopBloomFilter {
    private static final Logger log = LoggerFactory.getLogger(ShopBloomFilter.class);
    private static final String BLOOM_FILTER_KEY="bloom:shop:id";
    //预计元素数量
    private static final long EXPECTED_INSERTIONS=2000L;
    //误判率
    private static final double FPP=0.01;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private ShopMapper shopMapper;
    private RBloomFilter<Long> bloomFilter;
    @PostConstruct
    public void init(){
        // 初始化布隆过滤器（仅第一次需要）
        bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_KEY);
        if (!bloomFilter.isExists()) {
            bloomFilter.tryInit(EXPECTED_INSERTIONS, FPP);
            // 批量加载所有店铺ID到布隆过滤器
            loadAllShopIds();
        }
    }
    /**
     * 从数据库加载所有店铺ID并添加到布隆过滤器
     */
    private void loadAllShopIds() {
        try {
            List<Long> allShopIds = shopMapper.selectAllIds();
            if (allShopIds != null && !allShopIds.isEmpty()) {
                for (Long id : allShopIds) {
                    bloomFilter.add(id);
                }
                log.info("成功将{}个店铺ID加载到布隆过滤器", allShopIds.size());
            } else {
                log.warn("数据库中没有店铺数据");
            }
        } catch (Exception e) {
            log.error("加载店铺ID到布隆过滤器失败", e);
        }
    }
    // 判断商户ID是否可能存在
    public boolean mightContain(Long id) {
        return bloomFilter.contains(id);
    }
    // 添加商户ID到布隆过滤器（新增商户时调用）
    public void add(Long id) {
        bloomFilter.add(id);
    }
}


