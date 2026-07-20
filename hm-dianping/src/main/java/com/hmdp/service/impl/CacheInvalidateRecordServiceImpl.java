package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.CacheInvalidateRecord;
import com.hmdp.mapper.CacheInvalidateRecordMapper;
import com.hmdp.service.ICacheInvalidateRecordService;
import org.springframework.stereotype.Service;

@Service
public class CacheInvalidateRecordServiceImpl extends ServiceImpl<CacheInvalidateRecordMapper, CacheInvalidateRecord> implements ICacheInvalidateRecordService {
}
