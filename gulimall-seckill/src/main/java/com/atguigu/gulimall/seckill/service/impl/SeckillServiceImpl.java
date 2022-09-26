package com.atguigu.gulimall.seckill.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.to.mq.SeckillOrderTO;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberResponseVO;
import com.atguigu.gulimall.seckill.feign.CouponFeignService;
import com.atguigu.gulimall.seckill.feign.ProductFeignService;
import com.atguigu.gulimall.seckill.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.seckill.service.SeckillService;
import com.atguigu.gulimall.seckill.to.SeckillSkuRedisTO;
import com.atguigu.gulimall.seckill.vo.SeckillSessionWithSkusVO;
import com.atguigu.common.vo.SkuInfoVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    private CouponFeignService couponFeignService;

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RedissonClient redisson;
    
    @Autowired
    private RabbitTemplate rabbit;

    private final String SESSION_CACHE_PREFIX = "seckill:sessions:";
    private final String SECKILL_CACHE_PREFIX = "seckill:skus";
    private final String SKU_STOCK_SEMAPHORE = "seckill:stock:";//接商品随机码

    //上架秒杀商品
    @Override
    public void uploadSeckillSkuLatest3Days() {
        //扫描需要秒杀的活动
        R r = couponFeignService.getLatest3DaysSession();
        if (r.getCode() == 0) {
            //准备上架秒杀商品
            List<SeckillSessionWithSkusVO> data = r.getData("data", new TypeReference<List<SeckillSessionWithSkusVO>>() {});
            //缓存到redis
            //缓存活动信息
            saveSessionInfo(data);
            //缓存活动的关联商品信息
            saveSessionSkuInfo(data);
        }
    }

    //获取当前可以秒杀的商品
    @Override
    public List<SeckillSkuRedisTO> getCurrentSeckillSkus() {
        //确定当前时间属于那个场次
        Long time = new Date().getTime();
        Set<String> keys = redis.keys(SESSION_CACHE_PREFIX + "*");
        for (String key : keys) {
            //seckill:sessions:1664208000000_1664208000000
            String replace = key.replace(SESSION_CACHE_PREFIX, "");
            String[] s = replace.split("_");
            Long start = Long.parseLong(s[0]);
            Long end = Long.parseLong(s[1]);
            if(time >= start && time <= end) {
                //获取当前场次的秒杀商品
                List<String> range = redis.opsForList().range(key, -100, 100);
                BoundHashOperations<String, String, String> hashOps =
                        redis.boundHashOps(SECKILL_CACHE_PREFIX);
                List<String> list = hashOps.multiGet(range);
                if (list != null && list.size() > 0) {
                    List<SeckillSkuRedisTO> collect = list.stream().map(item -> {
                        //每个秒杀商品转成相应类型
                        SeckillSkuRedisTO redisTO = JSON.parseObject((String) item, SeckillSkuRedisTO.class);
//                        redisTO.setRandomCode(null);秒杀开始随机码
                        return redisTO;
                    }).collect(Collectors.toList());
                    return collect;
                }
                break;
            }
        }
        return null;
    }

    //查询秒杀商品信息 给gulimall-product
    @Override
    public SeckillSkuRedisTO getSkuSeckillInfo(Long skuId) {
        //找到所有需要参与秒杀的商品key
        BoundHashOperations<String, String, String> hashOps = redis.boundHashOps(SECKILL_CACHE_PREFIX);
        Set<String> keys = hashOps.keys();
        if (keys != null && keys.size() > 0) {
            //正则
            String reg = "\\d_"+skuId;
            for (String key : keys) {
                //匹配
                if ( Pattern.matches(reg, key) ) {
                    //获取到redis中秒杀商品
                    String json = hashOps.get(key);
                    SeckillSkuRedisTO redisTO = JSON.parseObject(json, SeckillSkuRedisTO.class);
                    //随机码遮盖还是返回
                    Long startTime = redisTO.getStartTime();
                    Long endTime = redisTO.getEndTime();
                    long current = new Date().getTime();
                    if (!(current >= startTime && current <= endTime)) {
                        redisTO.setRandomCode(null);
                    }
                    return redisTO;
                }
            }
        }

        return null;
    }

    //开始秒杀
    @Override
    public SeckillOrderTO kill(String killId, String key, Integer num) {
        long s1 = System.currentTimeMillis();

        MemberResponseVO memberResponseVO = LoginUserInterceptor.loginUser.get();
        Long userId = memberResponseVO.getId();
        //获取当前秒杀商品详细信息
        BoundHashOperations<String, String, String> hashOps = redis.boundHashOps(SECKILL_CACHE_PREFIX);
        //场次id + skuId
        String s = hashOps.get(killId);
        if (!StringUtils.isEmpty(s)) {
            SeckillSkuRedisTO redisTO = JSON.parseObject(s, SeckillSkuRedisTO.class);
            //合法性校验
            //时间段判断
            Long startTime = redisTO.getStartTime();
            Long endTime = redisTO.getEndTime();
            Long now = new Date().getTime();
            Long ttl = endTime - startTime;
            if (now >= startTime && now <= endTime) {
                //随机码校验
                String randomCode = redisTO.getRandomCode();
                String skuId = redisTO.getPromotionSessionId()+"_"+redisTO.getSkuId();
                if (randomCode.equals(key) && skuId.equals(killId)) {
                    //购物数量是否合理
                    Integer seckillLimit = redisTO.getSeckillLimit();
                    if (num <= seckillLimit) {
                        //验证用户是否已经购买过
                        //秒杀成功就取占位 userId_sessionId_skuId
                        String redisKey = userId + "_" + skuId;
                        Boolean aBoolean = redis.opsForValue().setIfAbsent(redisKey, num.toString(), ttl, TimeUnit.MILLISECONDS);
                        if (aBoolean) {
                            //第一次买
                            //分布式信号量
                            RSemaphore semaphore = redisson.getSemaphore(SKU_STOCK_SEMAPHORE + randomCode);

                            //尝试扣减信号量
                            boolean b = semaphore.tryAcquire(num);
                            if (b) {
                                //秒杀成功
                                //快速下单 发送MQ消息
                                String timeId = IdWorker.getTimeId();
                                //秒杀订单
                                SeckillOrderTO seckillOrderTO = new SeckillOrderTO();
                                seckillOrderTO.setOrderSn(timeId);
                                seckillOrderTO.setMemberId(userId);
                                seckillOrderTO.setNum(num);
                                seckillOrderTO.setPromotionSessionId(redisTO.getPromotionSessionId());
                                seckillOrderTO.setSeckillPrice(redisTO.getSeckillPrice());
                                seckillOrderTO.setSkuId(redisTO.getSkuId());
                                //商品信息
                                seckillOrderTO.setSkuInfo(redisTO.getSkuInfo());
                                //发消息
                                rabbit.convertAndSend(
                                        "order-event-exchange",
                                        "order.seckill.order",
                                        seckillOrderTO
                                );
                                long s3 = System.currentTimeMillis();
                                log.info("耗时..." + (s3 - s1));
                                return seckillOrderTO;//返回订单号
                            } else {
                                return null;
                            }
                        } else {
                            return null;
                        }
                    }
                } else {
                    return null;
                }
            } else {
                //不再时间内失败
                return null;
            }
        }
        return null;
    }

    //缓存活动信息
    private void saveSessionInfo(List<SeckillSessionWithSkusVO> sessions) {
        if (sessions != null && sessions.size() > 0) {
            sessions.stream().forEach(session -> {
                Long startTime = session.getStartTime().getTime();
                Long endTime = session.getEndTime().getTime();
                String key = SESSION_CACHE_PREFIX + startTime + "_" + endTime;
                //已经上架则不执行
                Boolean flag = redis.hasKey(key);
                if (!flag) {
                    //场次id + skuId
                    List<String> skuIds = session.getRelationSkus().stream().map(sku -> sku.getPromotionSessionId().toString() + "_" + sku.getSkuId().toString()).collect(Collectors.toList());
                    redis.opsForList().leftPushAll(key, skuIds);
                }
            });
        }
    }

    //缓存活动的关联商品信息
    private void saveSessionSkuInfo(List<SeckillSessionWithSkusVO> sessions) {
        if (sessions != null && sessions.size() > 0) {
            sessions.stream().forEach(session -> {
                //hash类型缓存
                BoundHashOperations<String, Object, Object> hashOps = redis.boundHashOps(SECKILL_CACHE_PREFIX);
                session.getRelationSkus().stream().forEach(sku -> {
                    //商品随机码：防刷
                    String token = UUID.randomUUID().toString().replace("-", "");
                    //场次id + skuId
                    String key = sku.getPromotionSessionId().toString() + "_" + sku.getSkuId().toString();

                    //防止重复缓存商品
                    if (!hashOps.hasKey(key)) {

                        //缓存商品
                        SeckillSkuRedisTO redisTO = new SeckillSkuRedisTO();
                        //sku基本信息
                        R skuBasicInfo = productFeignService.skuBasicInfo(sku.getSkuId());
                        if (skuBasicInfo.getCode() == 0) {
                            SkuInfoVO skuBasicInfoData = skuBasicInfo.getData("skuInfo", new TypeReference<SkuInfoVO>() {
                            });
                            redisTO.setSkuInfo(skuBasicInfoData);
                        }
                        //sku秒杀信息
                        BeanUtils.copyProperties(sku, redisTO);

                        //秒杀时间信息
                        redisTO.setStartTime(session.getStartTime().getTime());
                        redisTO.setEndTime(session.getEndTime().getTime());

                        //商品随机码：防刷
                        redisTO.setRandomCode(token);

                        String s = JSON.toJSONString(redisTO);
                        //场次id + skuId
                        hashOps.put(key, s);

                        //引入分布式信号量 限流
                        RSemaphore semaphore = redisson.getSemaphore(SKU_STOCK_SEMAPHORE + token);
                        semaphore.trySetPermits(sku.getSeckillCount());
                    }

                });
            });
        }
    }


}
