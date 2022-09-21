package com.atguigu.gulimall.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 封装订单提交的数据
 */
@Data
public class OrderSubmitVO {

    private Long addrId;//收货地址id
    private Integer payType;
    //无需提交需要购买的商品，去购物车再获取一遍
    //优惠,发票

    private String orderToken;//防重令牌
    private BigDecimal payPrice;//验价

    private String note;//备注信息
    //用户相关信息，从session中取


}
