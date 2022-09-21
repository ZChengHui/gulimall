package com.atguigu.gulimall.order.vo;

import lombok.Data;

import java.util.List;

@Data
public class WareSkuLockVO {

    private String orderSn;//订单号
    private List<OrderItemVO> locks;//需要锁库存的信息


}
