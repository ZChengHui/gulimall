package com.atguigu.gulimall.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

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
 */
@MapperScan("com.atguigu.gulimall.product.dao")
@EnableDiscoveryClient
@SpringBootApplication
public class GulimallProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallProductApplication.class, args);
    }

}
