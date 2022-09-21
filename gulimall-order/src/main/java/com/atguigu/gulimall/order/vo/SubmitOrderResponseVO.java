package com.atguigu.gulimall.order.vo;

import com.atguigu.gulimall.order.entity.OrderEntity;
import lombok.Data;

@Data
public class SubmitOrderResponseVO {

    private OrderEntity order;
    private Integer code;//错误状态码 0成功


}
