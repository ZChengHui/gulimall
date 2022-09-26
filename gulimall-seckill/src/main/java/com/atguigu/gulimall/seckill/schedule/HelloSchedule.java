package com.atguigu.gulimall.seckill.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 开启定时任务
 * @EnableScheduling + @Scheduled
 */
/**
 * 开启异步任务
 * @EnableAsync + @Async
 * TaskExecutionAutoConfiguration自动配置类
 * spring.task.excution.zzz=xxx
 *
 */
@Slf4j
//@Component
//@EnableScheduling
//@EnableAsync
public class HelloSchedule {

    /**
     * 秒 分 时 日 月 星期 年 quartz表达 星期日1
     * 秒 分 时 日 月 星期 spring中没有年 星期日7
     *
     * 定时任务不应该阻塞，系统默认是阻塞策略
     *      1）、让业务以异步方式提交到线程池
     *          CompletableFuture.runAsync(() -> {
     *             xxxService.hello();
     *         },excuter);
     *
     *      2）、定时任务线程池 不好使
     *              设置TaskSchedulingProperties
     *                  spring.task.scheduling.xxx=zzz
     *
     *      3）、定时任务异步执行
     *
     * 解决：定时任务+异步执行，防止线程阻塞
     */
//    @Async//异步不受阻塞
//    @Scheduled(cron = "* * * ? * 7")
    public void hello() throws InterruptedException {
        Thread.sleep(3000);
        log.info("hello>>>");
    }

}
