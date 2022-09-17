package com.atguigu.gulimall.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * 1、整合MyBatis-Plus
 *      1)、导入依赖
 *      2)、配置
 *          1>、配置数据源
 *              数据库驱动
 *              application.yml
 *          2>、配置MyBatis-Plus
 *              MapperScan("dao包名")
 *              yml配置xml映射路径
 * 2、逻辑删除
 *      1)、配置yml
 *      2)、注解@TableLogic
 * 3、规则校验
 *      1)、给entity添加注解，并编写返回message
 *      2)、controller参数添加valid注解
 *      3)、BindingResult返回校验结果
 *      4)、分组校验指定groups接口列表
 *      5)、自定义校验注解
 * 4、SpringCache
 *      1)、引入依赖
 *      2)、配置文件
 *      3)、注解
 *      @Cacheable 保存缓存
 *      @CacheEvict 删除缓存
 *      @CachePut 不影响方法更新缓存
 *      @Caching 组合操作
 *      @CacheConfig 在类级别共享缓存配置
 *      4)、开启缓存功能@EnableCaching
 *      5)、自定义缓存设置
 */

@EnableRedisHttpSession
@MapperScan("com.atguigu.gulimall.product.dao")
@EnableFeignClients(basePackages = "com.atguigu.gulimall.product.feign")
@EnableDiscoveryClient
@SpringBootApplication
public class GulimallProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallProductApplication.class, args);
    }

}
