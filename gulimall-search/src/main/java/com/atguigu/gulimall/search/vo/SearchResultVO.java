package com.atguigu.gulimall.search.vo;

import com.atguigu.common.to.es.SkuEsModel;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 返回给页面的信息
 */
@Data
public class SearchResultVO {

    //查询到的所有商品信息
    private List<SkuEsModel> products;

    //当前页码
    private Integer pageNum;
    //总记录数
    private Long total;
    //总页码
    private Integer totalPage;
    //导航页码
    private List<Integer> pageNav;

    //当前查询涉及的所有品牌(公共)
    private List<BrandVO> brands;

    //当前查询涉及的所有属性(公共)
    private List<AttrVO> attrs;

    //当前查询涉及的所有分类(公共)
    private List<CatalogVO> catalogs;

    //面包屑导航
    private List<NavVO> navs = new ArrayList<>();

    @Data
    public static class NavVO{
        private String navName;
        private String navValue;
        private String link;
    }

    @Data
    public static class BrandVO{

        private Long brandId;

        private String brandName;

        private String brandImg;

    }

    @Data
    public static class AttrVO{

        private Long attrId;

        private String attrName;

        private List<String> attrValue;

    }

    @Data
    public static class CatalogVO{

        private Long catalogId;

        private String catalogName;

    }

}
