package com.atguigu.gulimall.order;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
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
 */
@EnableRedisHttpSession
@EnableRabbit
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
public class GulimallOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallOrderApplication.class, args);
    }

}
