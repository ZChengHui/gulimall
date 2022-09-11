package com.atguigu.gulimall.product.vo;

import lombok.Data;
import lombok.ToString;

import java.util.List;

//销售属性
@ToString
@Data
public class SkuItemSaleAttrVO {
    private Long attrId;
    private String attrName;
    private List<AttrValueWithSkuIdVO> attrValues;
}
