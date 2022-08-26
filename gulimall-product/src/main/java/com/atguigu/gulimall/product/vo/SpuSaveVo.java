package com.atguigu.gulimall.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SpuSaveVo {

    private String spuName;
    private String spuDescription;
    private Long catalogId;
    private Long brandId;
    private BigDecimal weight;
    private int publishStatus;          //pms_spu_info

    private List<String> decript;       //pms_spu_info_desc

    private List<String> images;        //pms_spu_images

    private Bounds bounds;              //跨服务coupon

    private List<BaseAttrs> baseAttrs;  //pms_product_attr_value

    private List<Skus> skus;            //pms_sku_info

}