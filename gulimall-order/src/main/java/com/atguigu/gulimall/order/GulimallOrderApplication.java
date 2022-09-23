package com.atguigu.gulimall.order;

import com.alibaba.cloud.seata.GlobalTransactionAutoConfiguration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * 使用RabbitMQ
 * 1.引入amqp场景启动依赖 RabbitAutoConfiguration就会生效
 * 2.容器中自动配置
 *  RabbitTemplate AmqpAdmin
 *  CachingConnectionFactory RabbitMessagingTemplate
 *      属性配置文件开头 @ConfigurationProperties(prefix = "spring.rabbitmq")
 * 3.启动类加注解@EnableRabbit
 * 4.监听消息 使用@RabbitListener 必须有@EnableRabbit
 *
 * 本地事务失效问题
 *  事务使用代理对象控制，所以同一个对象内方法互调默认失效（绕过了代理）
 *  解决：
 *  1.使用AOP 引入aop-starter;spring-boot-starter-aop 引入aspectj
 *  2.@EnableAspectJAutoProxy(exposeProxy = true)开启动态代理，即使没有接口也可以创建动态代理
 *  3.本类内方法互调
 *      OrderServiceImpl orderService = (OrderServiceImpl) AopContextProxy();
 *      orderService.b();
 *      orderService.c();
 *
 * seata分布式事务
 *      1）. 每个微服务创建undo_log表
 *      2）。下载服务器 seata-server https://github.com/seata/seata/releases Maven:io.seata-all 0.7.1
     *      registry.conf
     *      registry { #seata的注册中心
     *          type = "nacos"
     *      }
     *      file.conf
 *      3）。使用DataSourceProxy 代理数据源
 *      4）。每个微服务导入 file.conf registry.conf
 *          修改 vgroup_mapping.gulimall-order-fescar-service-group = "default"
 *          为application.name
 *      5）。给分布式主事务标注@GlobalTransactional
 */
//@EnableAspectJAutoProxy(exposeProxy = true)
@EnableRedisHttpSession
@EnableRabbit
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication(exclude = GlobalTransactionAutoConfiguration.class)
public class GulimallOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallOrderApplication.class, args);
    }

}
