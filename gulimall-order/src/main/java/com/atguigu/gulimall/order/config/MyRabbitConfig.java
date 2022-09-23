package com.atguigu.gulimall.order.config;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class MyRabbitConfig {

    @Autowired
    private RabbitTemplate rabbit;

    //消息格式转换配置
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 定制RabbitTemplate
     * 发送端 保证送达
     *      1.服务收到消息就回调
     *          1.spring.rabbitmq.publisher-confirms=true 发送端确认
     *          2.设置确认回调ConfirmCallback
     *      2.消息正确抵达队列进行回调
     *          1.spring.rabbitmq.publisher-returns=true 发送端消息抵达队列确认
     *          2.spring.rabbitmq.template.mandatory=true 以异步发送，优先回调return confirm
     *
     * 消费端 保证处理
     *      1.默认是自动确认，只要消息收到，客户端自动确认，服务端则移除这个消息
     *      服务器宕机导致消息丢失，解决方式：将自动转手动ack确认
     *      2.如何签收
     *          false 非批量签收 true 批量签收
     *          channel.basicAck(deliveryTag, false);
     *
     *         拒绝签收 b: false 非批量签收 true 批量签收
     *         b1: false 丢弃 true 退回重入服务器
     *         channel.basicNack(deliveryTag, false, true);
     *
     */
    @PostConstruct // 构造器创建完再调用方法
    public void initRabbitTemplate() {

        RabbitTemplate.ConfirmCallback confirmCallback = new RabbitTemplate.ConfirmCallback() {
            /**
             * @param correlationData 当前消息唯一关联数据 id
             * @param b 是否成功收到
             * @param s 失败原因
             */
            @Override
            public void confirm(CorrelationData correlationData, boolean b, String s) {
                /**
                 * 手动ack
                 * 每一个发送的消息记录到数据库，定期扫描失败的消息
                 */
                //服务器收到
                System.out.println("confirm "+correlationData+"\nack "+b+"\ns "+s);
            }
        };
        //设置确认回调
        rabbit.setConfirmCallback(confirmCallback);

        RabbitTemplate.ReturnCallback returnCallback = new RabbitTemplate.ReturnCallback() {
            /**
             * 只要消息没有投递给指定队列，就出发这个失败回调
             * @param message 投递失败的详细消息
             * @param i 回复的状态码
             * @param s 回复的文本内容
             * @param s1 指定的交换机
             * @param s2 指定的路由键
             */
            @Override
            public void returnedMessage(Message message, int i, String s, String s1, String s2) {
                //报错误
                System.out.println("Fail MSG"+message+"\ncode="+i+"\nexchange="+s1+"\nrount-key="+s2);
            }
        };
        //设置消息抵达队列的确认回调
        rabbit.setReturnCallback(returnCallback);
    }
}
