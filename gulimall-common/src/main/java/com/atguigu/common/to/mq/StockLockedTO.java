package com.atguigu.common.to.mq;

import lombok.Data;

import java.util.List;

@Data
public class StockLockedTO {

    private Long id;//库存工作单id
    private StockDetailTO detail;//工作单详情

}
