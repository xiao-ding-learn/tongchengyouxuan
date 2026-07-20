package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 缓存失效记录实体
 * 用于兜底机制，确保缓存最终失效
 */
@Data
@TableName("tb_cache_invalidate_record")
public class CacheInvalidateRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shopId;

    private Integer status;

    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
