package com.atguigu.gulimall.product.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

//@EnableConfigurationProperties(ThreadPoolConfigProperties.class)
//配置线程池 从容器中获取配置属性
@Configuration
public class MyThreadConfig {

    @Bean
    public ThreadPoolExecutor threadPoolExecutor(ThreadPoolConfigProperties pool) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                pool.getCoreSize(),
                pool.getMaxSize(),
                pool.getKeppAliveTime(),
                 TimeUnit.SECONDS,
                 new LinkedBlockingDeque<>(100000),
                 Executors.defaultThreadFactory(),
                 new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

}
