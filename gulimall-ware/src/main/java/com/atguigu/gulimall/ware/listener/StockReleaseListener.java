package com.atguigu.gulimall.ware.listener;

import com.atguigu.common.to.mq.OrderTO;
import com.atguigu.common.to.mq.StockLockedTO;
import com.atguigu.gulimall.ware.service.WareSkuService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RabbitListener(queues = "stock.release.stock.queue")
public class StockReleaseListener {

    @Autowired
    private WareSkuService wareSkuService;

    /**
     * 1.库存自动解锁
     * 下单成功，库存锁定成功，其他业务调用失败，导致订单回滚
     *      之前锁定的库存就要自动解锁
     * 2.订单失败
     * 库存不足，锁库存失败
     *
     * 解锁库存消息失败，启动手动ack channel com.rabbitmq.client.Channel;
     * @param to
     * @param message
     */
    @RabbitHandler
    public void handleStockLockedRelease(StockLockedTO to, Message message, Channel channel) throws IOException {
        System.out.println("收到解锁库存的消息");
        try {
            wareSkuService.unLockStock(to);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            //重新入队
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
        }
    }

    //补偿逻辑
    @RabbitHandler
    public void handleOrderCloseRelease(OrderTO order, Message message, Channel channel) throws IOException {
        System.out.println("收到订单取消的消息，准备解锁库存");
        try {
            wareSkuService.unLockStock(order);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), true);
        }
    }

}
