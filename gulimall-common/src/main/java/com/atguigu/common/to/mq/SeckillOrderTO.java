package com.atguigu.common.to.mq;

import com.atguigu.common.vo.SkuInfoVO;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SeckillOrderTO {

    /**
     * 订单号
     */
    private String orderSn;
    /**
     * 活动场次id
     */
    private Long promotionSessionId;
    /**
     * 商品id
     */
    private Long skuId;
    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;
    /**
     * 秒杀件数
     */
    private Integer num;
    /**
     * 会员id
     */
    private Long memberId;
    /**
     * 商品sku信息
     */
    private SkuInfoVO skuInfo;

}
