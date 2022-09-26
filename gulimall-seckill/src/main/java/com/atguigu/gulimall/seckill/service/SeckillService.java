package com.atguigu.gulimall.seckill.service;


import com.atguigu.common.to.mq.SeckillOrderTO;
import com.atguigu.gulimall.seckill.to.SeckillSkuRedisTO;

import java.util.List;

public interface SeckillService {

    void uploadSeckillSkuLatest3Days();

    List<SeckillSkuRedisTO> getCurrentSeckillSkus();

    SeckillSkuRedisTO getSkuSeckillInfo(Long skuId);

    SeckillOrderTO kill(String killId, String key, Integer num);
}
