package com.atguigu.gulimall.ware.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FareResponseVO {

    private MemberAddressVO address;
    private BigDecimal fare;

}
