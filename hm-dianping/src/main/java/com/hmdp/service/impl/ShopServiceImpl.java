package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.CacheInvalidateMQConfig;
import com.hmdp.config.ShopBloomFilter;
import com.hmdp.config.ShopLocalCache;
import com.hmdp.dto.CacheInvalidateMessage;
import com.hmdp.dto.Result;
import com.hmdp.entity.CacheInvalidateRecord;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.ICacheInvalidateRecordService;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
     @Resource
     private StringRedisTemplate stringRedisTemplate;
     public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
     @Resource
     private ShopLocalCache shopLocalCache;
    @Resource
    private ShopBloomFilter shopBloomFilter;
    @Resource
    private ShopMapper shopMapper;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private ICacheInvalidateRecordService cacheInvalidateRecordService;

    
    @Override
    public Result getShopById(Long id) {
        /*Shop shop = queryWithMutex(id);
        if(shop == null){
            return Result.fail("店铺不存在!");
        }
        return Result.ok(shop);*/
        //三级缓存优化
       // 1. 查 L1 布隆过滤器（防穿透）
       if (!shopBloomFilter.mightContain(id)) {
           return Result.fail("店铺不存在!");
       }
       // 2. 查 L2 本地缓存
       Shop shop = shopLocalCache.get(id);
       if (shop != null) {
           return Result.ok(shop);
       }
       // 3. 查 L3 Redis 缓存
       Shop shopRedis = queryWithLogicalExpire(id);
       if (shopRedis != null ) {
           // 回写 L1 本地缓存
           shopLocalCache.put(id, shopRedis);
           return Result.ok(shopRedis);
       }

       // 检查Redis中是否存在空字符串（表示数据库中不存在）
       String redisKey = RedisConstants.CACHE_SHOP_KEY + id;
       Boolean exists = stringRedisTemplate.hasKey(redisKey);
       if (BooleanUtil.isTrue(exists)) {
           // Redis中存在该key但值为空，说明是缓存的空值，表示数据库中不存在
           return Result.fail("店铺不存在!");
       }

       // 4. 缓存未命中，查数据库
       shop = shopMapper.selectById(id);
       if (shop == null) {
           // 极端情况：布隆过滤器误判，实际不存在,缓存短期空字符串避免穿透
           String nullKey = RedisConstants.CACHE_SHOP_KEY + id;
           stringRedisTemplate.opsForValue().set(nullKey,"" , 2 ,TimeUnit.MINUTES);
           return Result.fail("店铺不存在!");

       }

       //首次写入redis
       long baseExpireSeconds = 30 * 60;
       long randomOffsetSeconds = new Random().nextInt(600) - 300;
       long finalExpireSeconds = baseExpireSeconds + randomOffsetSeconds;
       this.saveShopRedis(id,shop,finalExpireSeconds);
       // 5. 回写 L1 本地缓存
       shopLocalCache.put(id, shop);
        //Shop shop = shopMapper.selectById(id);
        return Result.ok(shop);
}



    //缓存击穿逻辑删除解决
    public Shop queryWithLogicalExpire(Long id){
        //从redis查询
        String key= RedisConstants.CACHE_SHOP_KEY+id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //不存在直接返回null
        if(StrUtil.isBlank(shopJson)){
            return null;
        }
        //命中，把json序列化为对象
        // 判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        //过期尝试获取锁开始重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            shopJson = stringRedisTemplate.opsForValue().get(key);
            if(StrUtil.isNotBlank(shopJson)){
                RedisData checkRedisData = JSONUtil.toBean(shopJson, RedisData.class);
                LocalDateTime checkExpireTime = checkRedisData.getExpireTime();
                if(checkExpireTime.isAfter(LocalDateTime.now())){
                    return JSONUtil.toBean((JSONObject) checkRedisData.getData(), Shop.class);
                }
            }
            //开启新线程执行任务
         CACHE_REBUILD_EXECUTOR.submit(()->{
             try {
                 long baseExpireSeconds = 30 * 60;
                 long randomOffsetSeconds = new Random().nextInt(600) - 300;
                 long finalExpireSeconds = baseExpireSeconds + randomOffsetSeconds;
                 this.saveShopRedis(id,finalExpireSeconds);
             }finally {
                 //释放锁
                 deleteLock(lockKey);
             }
         });
        }
        //未获取到锁返回旧数据
        return shop;

    }
    //缓存重建
    public void saveShopRedis(Long id,Long expireSeconds){
        //查询店铺数据
        Shop shop = getById(id);
        //封装逻辑过期时间
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData),
                24,TimeUnit.HOURS);

    }
    public void saveShopRedis(Long id,Shop shop,Long expireSeconds){
        //封装逻辑过期时间
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData),
                24,TimeUnit.HOURS);

    }
    /*//缓存击穿解决方案-互斥锁
    public Shop queryWithMutex(Long id){
        //从redis查询
        String key= RedisConstants.CACHE_SHOP_KEY+id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //存在直接返回(有数据)
        if(StrUtil.isNotBlank(shopJson)){
            //string转换成bean
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是redis存的是null还是无数据
        if(shopJson != null){
            return null;
        }
        //无数据尝试获取锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        try {
            boolean isLock = tryLock(lockKey);
            //判断是否获取锁成功
            if(!isLock){
                //失败休眠一段时间，再次尝试
                Thread.sleep(50);
                queryWithMutex(id);
            }
            //成功去数据库查询，写入缓存
            Shop shop = getById(id);
            if(shop == null){
                //数据库不存在，写入空数据，避免缓存穿透
                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            deleteLock(key);
        }
    }
    */

    //获取锁的方法
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }
    //释放锁的方法
    private Boolean deleteLock(String key){
        Boolean flag = stringRedisTemplate.delete(key);
        return BooleanUtil.isTrue(flag);
    }
    /*
    //缓存穿透写空值解决方案
    public Shop queryWithPassThrough(Long id){
        //从redis查询
        String key= RedisConstants.CACHE_SHOP_KEY+id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //存在直接返回(有数据)
        if(StrUtil.isNotBlank(shopJson)){
            //string转换成bean
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是redis存的是null还是无数据
        if(shopJson != null){
            return null;
        }
        //无数据查询数据库，写回缓存并返回
        Shop shop = getById(id);
        if(shop == null){
            //数据库不存在，写入空数据，避免缓存穿透
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }*/

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if(id == null){
          return Result.fail("店铺id不能为空");
        }
        
        updateById(shop);
        
        boolean invalidateSuccess = false;
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        
        try {
            stringRedisTemplate.delete(key);
            shopLocalCache.invalidate(id);
            invalidateSuccess = true;
            log.info("缓存失效成功，shopId: {}", id);
        } catch (Exception e) {
            log.error("缓存失效失败，shopId: {}, error: {}", id, e.getMessage());
        }
        
        if (!invalidateSuccess) {
            CacheInvalidateRecord record = new CacheInvalidateRecord();
            record.setShopId(id);
            record.setStatus(0);
            record.setRetryCount(0);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            cacheInvalidateRecordService.save(record);
            
            try {
                CacheInvalidateMessage message = new CacheInvalidateMessage(
                    id, LocalDateTime.now(), 0);
                rabbitTemplate.convertAndSend(
                    CacheInvalidateMQConfig.CACHE_INVALIDATE_EXCHANGE,
                    CacheInvalidateMQConfig.CACHE_INVALIDATE_ROUTING_KEY,
                    message
                );
                log.info("缓存失效消息已发送到MQ，shopId: {}", id);
            } catch (Exception e) {
                log.error("缓存失效消息发送失败，shopId: {}, error: {}", id, e.getMessage());
            }
        }
        
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否需要根据坐标查询
        if(x == null || y == null){
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page);
        }
        //计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //查询redis，按照距离分页，排序
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        if(results == null){
            return Result.ok(Collections.emptyList());
        }
        //解析出id
        //截取from-end部分
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if(list.size() <= from){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids=new ArrayList<>(list.size());
        Map<String,Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from)
                .forEach(result->{
                    String shopIdStr = result.getContent().getName();
                    ids.add(Long.valueOf(shopIdStr));
                    Distance distance = result.getDistance();
                    distanceMap.put(shopIdStr,distance);
                });
        //根据id查询shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("order by field(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
