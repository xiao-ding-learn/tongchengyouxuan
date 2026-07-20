package com.hmdp;

import com.hmdp.service.IShopService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.hmdp.dto.Result;
@SpringBootTest
public class testCache {
        @Autowired
        private IShopService shopService;
        @Test
        public void testThreeLevelCache() {
            Long shopId = 1L;

            // 1. 第一次查询：走 DB
            System.out.println("=== 第一次查询 ===");
            Result result1 = shopService.getShopById(shopId);
            System.out.println("结果: " + result1);

            // 2. 第二次查询：走 L1 本地缓存
            System.out.println("=== 第二次查询 ===");
            Result result2 = shopService.getShopById(shopId);
            System.out.println("结果: " + result2);

            // 3. 测试缓存穿透
            System.out.println("=== 测试不存在的 ID ===");
            Result result3 = shopService.getShopById(999999999L);
            System.out.println("结果: " + result3); // 预期返回 "店铺不存在!"
        }
    }

