package com.atguigu.gulimall.coupon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 如何使用Nacos作为配置中心
 * 1.引入依赖spring-cloud-alibaba-nacos-config
 * 2.创建bootstrap.propertis 配置应用名，服务器地址名
 * 3.nacos服务器添加数据集ID和具体配置信息
 * 4.动态获取刷新配置，Controller添加注解@RefreshScope、@Value
 * 5.配置中心优先级更高
 *
 * 细节
 * 1.命名空间
 * 2.配置集
 * 3.配置集ID - DataID
 * 4.配置分组
 *
 * 微服务用命名空间区分
 * 多环境用分组区分
 */
@EnableDiscoveryClient
@SpringBootApplication
public class GulimallCouponApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallCouponApplication.class, args);
    }
}
