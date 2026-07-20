package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 缓存失效消息DTO
 * 用于异步删除缓存的消息传递
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheInvalidateMessage {
    
    private Long shopId;
    
    private LocalDateTime createTime;
    
    private Integer retryCount;
}
