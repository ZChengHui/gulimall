package com.atguigu.gulimall.product.vo;

import com.atguigu.gulimall.product.entity.SkuImagesEntity;
import com.atguigu.gulimall.product.entity.SkuInfoEntity;
import com.atguigu.gulimall.product.entity.SpuInfoDescEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.util.List;

@Data
public class SkuItemVO {

    //1.sku基本信息 pms_sku_info
    SkuInfoEntity info;

    //是否有货
    Boolean hasStock = true;

    //2.sku图片信息 pms_sku_images
    List<SkuImagesEntity> images;

    //3.spu销售属性
    List<SkuItemSaleAttrVO> saleAttr;

    //4.spu介绍
    SpuInfoDescEntity desp;

    //5.所有组规格参数信息
    List<SpuItemAttrGroupAttrVO> groupAttrs;

    //当前商品秒杀优惠信息
    SeckillInfoVO seckillInfo;

}
