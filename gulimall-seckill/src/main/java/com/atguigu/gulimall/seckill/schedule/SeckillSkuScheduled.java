package com.atguigu.gulimall.seckill.schedule;

import com.atguigu.gulimall.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀商品定时上架
 * 每天晚上9点，上架最近三天需要的秒杀商品
 *      当天00:00:00 - 23:59:59
 *      明天00:00:00 - 23:59:59
 *      后天00:00:00 - 23:59:59
 */
@Slf4j
@Service
public class SeckillSkuScheduled {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private RedissonClient redisson;

    private final String UPLOAD_LOCK = "seckill:upload:lock";

    /**
     * 幂等性处理
     * 分布式锁
     */
    @Scheduled(cron = "*/5 * * * * ?")
    public void uploadSeckillSku() {
        //重复上架不处理
        log.info("上架了");
        //加分布式锁，被锁定的业务完成，状态更新后，释放所，其他人才会获取到最新业务状态
        RLock lock = redisson.getLock(UPLOAD_LOCK);
        lock.lock(10, TimeUnit.SECONDS);
        try {
            seckillService.uploadSeckillSkuLatest3Days();
        } finally {
            lock.unlock();
        }
    }

}
