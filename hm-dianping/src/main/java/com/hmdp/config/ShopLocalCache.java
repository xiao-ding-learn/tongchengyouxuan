package com.hmdp.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hmdp.entity.Shop;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Component
public class ShopLocalCache {
    //L1本地缓存
    private Cache<Long, Shop> shopCache;
    @PostConstruct
    public void init(){
        shopCache = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(5 + new Random().nextInt(3), TimeUnit.MINUTES)
                .build();
    }
    //从本地缓存获取
    public Shop get(Long id){
        return shopCache.getIfPresent(id);
    }
    //写入本地缓存
    public void put(Long id,Shop shop){
        shopCache.put(id,shop);
    }
    //清除本地缓存
    public void invalidate(Long id){
        shopCache.invalidate(id);
    }
}
