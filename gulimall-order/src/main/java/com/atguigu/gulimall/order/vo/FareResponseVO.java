package com.atguigu.gulimall.order.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FareResponseVO {

    private MemberAddressVO address;
    private BigDecimal fare;

}
