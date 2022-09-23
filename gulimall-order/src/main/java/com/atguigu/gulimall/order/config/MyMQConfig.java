package com.atguigu.gulimall.order.config;

import com.atguigu.gulimall.order.entity.OrderEntity;
import com.rabbitmq.client.Channel;
import javafx.beans.binding.ObjectExpression;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

//一个交换机 两个队列
@Configuration
public class MyMQConfig {

    //死信队列
    @Bean
    public Queue orderDelayQueue() {
        //String name, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments
        /**
         * x-dead-letter-exchange:order-event-exchange
         * x-dead-letter-routing-key:order.release.order
         * x-message-ttl:60000
         */
        Map<String, Object> arg = new HashMap<>();
        arg.put("x-dead-letter-exchange", "order-event-exchange");
        arg.put("x-dead-letter-routing-key", "order.release.order");
        arg.put("x-message-ttl", 60000);
        Queue queue = new Queue("order.delay.queue", true, false, false, arg);
        return queue;
    }

    //普通队列
    @Bean
    public Queue orderReleaseOrderQueue() {
        Queue queue = new Queue("order.release.order.queue", true, false, false);
        return queue;
    }

    //交换机
    @Bean
    public Exchange orderEventExchange() {
        //String name, boolean durable, boolean autoDelete, Map<String, Object> arguments
        TopicExchange topicExchange = new TopicExchange("order-event-exchange",true,false);
        return topicExchange;
    }

    //绑定参数
    @Bean
    public Binding orderCreateOrderBinding() {
        //String destination, Binding.DestinationType destinationType, String exchange, String routingKey, Map<String, Object> arguments
        Binding binding = new Binding(
                "order.delay.queue",//死信队列
                Binding.DestinationType.QUEUE,
                "order-event-exchange",//交换机
                "order.create.order",//路由键
                null);
        return binding;
    }

    @Bean
    public Binding orderReleaseOrderBinding() {
        Binding binding = new Binding(
                "order.release.order.queue",//普通队列
                Binding.DestinationType.QUEUE,
                "order-event-exchange",//交换机
                "order.release.order",//路由键
                null);
        return binding;
    }

    /**
     * 订单释放 和 库存释放 绑定
     * @return
     */
    @Bean
    public Binding orderReleaseOtherBinding() {
        Binding binding = new Binding(
                "stock.release.stock.queue",//普通队列
                Binding.DestinationType.QUEUE,
                "order-event-exchange",//交换机
                "order.release.other.#",//路由键
                null);
        return binding;
    }

}
