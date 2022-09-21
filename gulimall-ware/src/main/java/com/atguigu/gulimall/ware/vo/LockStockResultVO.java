package com.atguigu.gulimall.ware.vo;

import lombok.Data;

//库存锁定返回结果
@Data
public class LockStockResultVO {

    private Long skuId;
    private Integer num;
    private boolean locked;

}
