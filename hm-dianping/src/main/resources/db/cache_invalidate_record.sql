-- 缓存失效记录表
-- 用于兜底机制，确保缓存最终失效
CREATE TABLE IF NOT EXISTS `tb_cache_invalidate_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) NOT NULL COMMENT '店铺ID',
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '状态：0-待处理，1-已成功，2-失败',
  `retry_count` int(11) NOT NULL DEFAULT 0 COMMENT '重试次数',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_shop_id` (`shop_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缓存失效记录表';
