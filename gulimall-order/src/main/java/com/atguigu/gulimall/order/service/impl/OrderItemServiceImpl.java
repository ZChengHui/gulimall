package com.atguigu.gulimall.order.service.impl;

import com.atguigu.gulimall.order.entity.OrderReturnReasonEntity;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.order.dao.OrderItemDao;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.atguigu.gulimall.order.service.OrderItemService;


@Service("orderItemService")
@RabbitListener(queues = {"hello-queue"})
public class OrderItemServiceImpl extends ServiceImpl<OrderItemDao, OrderItemEntity> implements OrderItemService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderItemEntity> page = this.page(
                new Query<OrderItemEntity>().getPage(params),
                new QueryWrapper<OrderItemEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * TODO MQ Test
     * queues 监听的所有队列
     * Message 原生消息详细信息
     * 可自动解析出对象内容
     *
     * 订单服务启动多个，同一个消息只能有一个客户端收到
     * 前一个消息处理完，才会接收下一个消息
     *
     * @RabbitListener：作用类和方法上
     * @RabbitHandler：作用方法上
     */
    @RabbitHandler
    public void receiveMyMsg(Message msg, OrderReturnReasonEntity content, Channel channel) throws IOException {
        //通道内自增的序号
        long deliveryTag = msg.getMessageProperties().getDeliveryTag();
        //false 非批量签收 true 批量签收
        //channel.basicAck(deliveryTag, false);

        //拒绝签收 b: false 非批量签收 true 批量签收
        //b1: false 丢弃 true 退回重入服务器
        channel.basicNack(deliveryTag, false, false);
//        channel.basicReject();
        System.out.println("接收到消息"+msg+"\n内容： "+content);
    }

}