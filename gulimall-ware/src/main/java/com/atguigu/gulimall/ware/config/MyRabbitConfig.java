package com.atguigu.gulimall.ware.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class MyRabbitConfig {

   @Autowired
   private RabbitTemplate rabbit;

   //消息格式转换配置
   @Bean
   public MessageConverter messageConverter() {
       return new Jackson2JsonMessageConverter();
   }

   //交换机
   @Bean
   public Exchange stockEvent() {
      //String name, boolean durable, boolean autoDelete, Map<String, Object> arguments
      TopicExchange topicExchange = new TopicExchange("stock-event-exchange", true, false);
      return topicExchange;
   }

   //simple queue
   @Bean
   public Queue stockReleaseStockQueue() {
      //String name, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments
      Queue queue = new Queue("stock.release.stock.queue",true,false,false);
      return queue;
   }

   //delay queue
   @Bean
   public Queue stockDelayQueue() {
      /**
       * x-dead-letter-exchange:order-event-exchange
       * x-dead-letter-routing-key:order.release.order
       * x-message-ttl:60000
       */
      Map<String, Object> map = new HashMap<>();
      map.put("x-dead-letter-exchange","stock-event-exchange");
      map.put("x-dead-letter-routing-key","stock.release");
      map.put("x-message-ttl",65000);
      Queue queue = new Queue("stock.delay.queue",true,false,false,map);
      return queue;
   }

   @Bean
   public Binding stockReleaseBinding() {
      //String destination, Binding.DestinationType destinationType, String exchange, String routingKey, Map<String, Object> arguments
      Binding binding = new Binding(
              "stock.release.stock.queue",
              Binding.DestinationType.QUEUE,
              "stock-event-exchange",
              "stock.release.#",
              null
      );
      return binding;
   }
   @Bean
   public Binding stockLockedBinding() {
      //String destination, Binding.DestinationType destinationType, String exchange, String routingKey, Map<String, Object> arguments
      Binding binding = new Binding(
              "stock.delay.queue",
              Binding.DestinationType.QUEUE,
              "stock-event-exchange",
              "stock.locked",
              null
      );
      return binding;
   }

}
