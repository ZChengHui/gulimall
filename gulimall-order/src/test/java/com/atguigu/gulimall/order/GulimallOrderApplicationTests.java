package com.atguigu.gulimall.order;

import com.atguigu.gulimall.order.entity.OrderReturnReasonEntity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.UUID;

@SpringBootTest
@RunWith(SpringRunner.class)
public class GulimallOrderApplicationTests {

    /**
     * 如何创建Exchange Queue Binding
     *      使用AmqpAdmin创建
     */
    @Autowired
    private AmqpAdmin amqpAdmin;

    /**
     * 如何收发消息
     *      使用RabbitTemplate
     */
    @Autowired
    private RabbitTemplate rabbit;

    //创建交换机
    @Test
    public void createExchange() {
        Exchange exchange = new DirectExchange(
                "hello-exchange",
                true,
                false);
        amqpAdmin.declareExchange(exchange);
    }

    //创建队列
    @Test
    public void createQueue() {
        Queue queue = new Queue(
                "hello-queue",
                true,
                false,
                false
        );
        amqpAdmin.declareQueue(queue);
    }

    //创建绑定
    @Test
    public void createBinding() {
        /**
         * String destination, 目的地
         * Binding.DestinationType destinationType, 目的地类型
         * String exchange, 交换机
         * String routingKey, 路由键
         * Map<String, Object> arguments)
         */
        Binding binding = new Binding(
            "hello-queue",
                Binding.DestinationType.QUEUE,
                "hello-exchange",
                "hello",
                null
        );
        amqpAdmin.declareBinding(binding);
    }

    //发送消息
    @Test
    public void sendMsg() {
        //发送字符串消息
//        rabbit.convertAndSend(
//                "hello-exchange",
//                "hello",
//                "Hello World!");
        //发送对象消息 对象要实现序列化机制
        OrderReturnReasonEntity entity = new OrderReturnReasonEntity();
        entity.setId(110L);
        entity.setName("兔子");
        entity.setCreateTime(new Date());
        rabbit.convertAndSend(
                "hello-exchange",
                "hello",
                entity,
                new CorrelationData(UUID.randomUUID().toString())
        );
        /**
         * 订单服务启动多个，同一个消息只能有一个客户端收到
         * 前一个消息处理完，才会接收下一个消息
         *
         * 处理不同类型消息
         * @RabbitListener：作用类和方法上 监听队列范围
         *      @RabbitHandler：作用方法上 重载参数来接收不同消息
         */
    }

}
