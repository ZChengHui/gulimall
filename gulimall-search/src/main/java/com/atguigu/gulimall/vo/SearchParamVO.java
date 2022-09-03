package com.atguigu.gulimall.vo;

import lombok.Data;

import java.util.List;

/**
 * 封装页面传递的查询条件
 * eg. catalog3Id=225&keyword=xiaomi&sort=saleCount_desc
 */
@Data
public class SearchParamVO {

    private String keyword;

    private Long catalog3Id;

    /**
     * 多种排序方式
     * 销量 saleCount_asc/desc
     * 价格 skuPrice_asc/desc
     * 热度 hotScore_asc/desc
     */
    private String sort;

    /**
     * 多种过滤条件
     * hasStock(是否有货)
     * skuPrice(价格区间)500_1000/_1000/500_
     * brandId(品牌名称)
     * attrs(参数类型)
     */
    private Integer hasStock = 1;//0无库存 1有库存

    private String skuPrice;

    private List<Long> brandId;

    private List<String> attrs;

    //分页页码
    private Integer pageNum = 1;
}
