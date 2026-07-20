package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryType() {
        //查询缓存
        String shopTypeJson = stringRedisTemplate.opsForValue().get(RedisConstants.SHOP_TYPE_LIST);
        if(StrUtil.isNotBlank(shopTypeJson)){
            //存在直接返回
            List<ShopType> list = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(list);
        }
        //不存在查询数据库，写入缓存并返回
        List<ShopType> list = query().orderByAsc("sort").list();
        if(list == null){
            return  Result.fail("店铺分类不存在");
        }
        String jsonStr = JSONUtil.toJsonStr(list);
        stringRedisTemplate.opsForValue().set(RedisConstants.SHOP_TYPE_LIST,jsonStr);
        return Result.ok(list);
    }
}
