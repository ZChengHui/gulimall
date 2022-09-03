package com.atguigu.gulimall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.gulimall.search.EsConstant;
import com.atguigu.gulimall.search.config.GulimallElasticSearchConfig;
import com.atguigu.gulimall.search.service.MallSearchService;
import com.atguigu.gulimall.vo.SearchParamVO;
import com.atguigu.gulimall.vo.SearchResultVO;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MallSearchServiceImpl implements MallSearchService {

    @Autowired
    private RestHighLevelClient client;

    /**
     * @param param ES检索参数
     * @return 返回结果包含页面的所有信息
     */
    @Override
    public SearchResultVO search(SearchParamVO param) {
        //动态构建DSL语句

        //准备检索请求
        SearchRequest searchRequest = buildSearchRequest(param);
        SearchResultVO resultVO = null;
        try {
            //执行检索请求
            SearchResponse response = client.search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);

            //分析响应数据 封装成VO
            resultVO = buildSearchResult(response, param);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private SearchResultVO buildSearchResult(SearchResponse response, SearchParamVO param) {
        SearchResultVO resultVO = new SearchResultVO();
        SearchHits hits = response.getHits();

        List<SkuEsModel> esModels = new ArrayList<>();
        if (hits.getHits() != null && hits.getHits().length > 0) {
            for (SearchHit hit : hits) {
                String sourceAsString = hit.getSourceAsString();
                SkuEsModel esModel = JSON.parseObject(sourceAsString, SkuEsModel.class);
                //设置高亮skuTitle
                if (!StringUtils.isEmpty(param.getKeyword())) {
                    HighlightField skuTitle = hit.getHighlightFields().get("skuTitle");
                    String highlight = skuTitle.getFragments()[0].string();
                    esModel.setSkuTitle(highlight);
                }
                esModels.add(esModel);
            }
        }

        //返回查询到的商品
        resultVO.setProducts(esModels);

        //当前商品涉及到的分类信息
        ParsedLongTerms catalogAgg = response.getAggregations().get("catalogAgg");
        List<SearchResultVO.CatalogVO> catalogVOS = new ArrayList<>();
        //获取分类聚合信息
        List<? extends Terms.Bucket> catalogAggBuckets = catalogAgg.getBuckets();
        catalogAggBuckets.forEach(bucket -> {
            SearchResultVO.CatalogVO catalogVO = new SearchResultVO.CatalogVO();
            //一级聚合分类ID
            Long catalogId = bucket.getKeyAsNumber().longValue();
            catalogVO.setCatalogId(catalogId);

            //得到分类名聚合对象
            ParsedStringTerms catalogNameAgg = bucket.getAggregations().get("catalogNameAgg");
            //拿到第一个聚合，分类名聚合信息
            String catalogName = catalogNameAgg.getBuckets().get(0).getKeyAsString();
            catalogVO.setCatalogName(catalogName);

            catalogVOS.add(catalogVO);
        });

        resultVO.setCatalogs(catalogVOS);

        //当前商品涉及到的品牌信息
        List<SearchResultVO.BrandVO> brandVOS = new ArrayList<>();
        ParsedLongTerms brandAgg = response.getAggregations().get("brandAgg");
        List<? extends Terms.Bucket> brandAggBuckets = brandAgg.getBuckets();
        brandAggBuckets.forEach(bucket -> {
            SearchResultVO.BrandVO brandVO = new SearchResultVO.BrandVO();
            //品牌ID
            Long brandId = bucket.getKeyAsNumber().longValue();
            brandVO.setBrandId(brandId);

            ParsedStringTerms brandImgAgg = bucket.getAggregations().get("brandImgAgg");
            //品牌图片
            String brandImg = brandImgAgg.getBuckets().get(0).getKeyAsString();
            brandVO.setBrandImg(brandImg);

            ParsedStringTerms brandValueAgg = bucket.getAggregations().get("brandNameAgg");
            //品牌名
            String brandName = brandValueAgg.getBuckets().get(0).getKeyAsString();
            brandVO.setBrandName(brandName);

            brandVOS.add(brandVO);
        });

        resultVO.setBrands(brandVOS);

        //当前商品涉及到的属性信息
        List<SearchResultVO.AttrVO> attrVOS = new ArrayList<>();
        ParsedNested attrAgg = response.getAggregations().get("attrAgg");
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> attrIdAggBuckets = attrIdAgg.getBuckets();
        attrIdAggBuckets.forEach(bucket -> {
            SearchResultVO.AttrVO attrVO = new SearchResultVO.AttrVO();
            //属性ID
            Long attrId = bucket.getKeyAsNumber().longValue();
            attrVO.setAttrId(attrId);
            //属性名
            ParsedStringTerms attrNameAgg = bucket.getAggregations().get("attrNameAgg");
            String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
            attrVO.setAttrName(attrName);
            //多个属性值
            ParsedStringTerms attrValueAgg = bucket.getAggregations().get("attrValueAgg");
            List<String> attrValue = attrValueAgg.getBuckets().stream().map(item -> {
                String attrItemValue = item.getKeyAsString();
                return attrItemValue;
            }).collect(Collectors.toList());
            attrVO.setAttrValue(attrValue);

            attrVOS.add(attrVO);
        });

        resultVO.setAttrs(attrVOS);

        //分页信息
        Long total = hits.getTotalHits().value;
        Integer totalPage = (int)((total + EsConstant.PRODUCT_PAGESIZE - 1) / EsConstant.PRODUCT_PAGESIZE);
        resultVO.setPageNum(param.getPageNum());
        resultVO.setTotal(total);
        resultVO.setTotalPage(totalPage);

        return resultVO;
    }

    /**构建检索请求
     * 模糊匹配，过滤（属性，分类，品牌，价格区间，库存），排序，分页，高亮，聚合分析
     * @return
     */
    private SearchRequest buildSearchRequest(SearchParamVO param) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        //模糊匹配，过滤（属性，分类，品牌，价格区间，库存）
        //1. bool query
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        //1.1 must
        if (!StringUtils.isEmpty(param.getKeyword())) {
            //match 模糊匹配
            boolQuery.must(QueryBuilders.matchQuery("skuTitle", param.getKeyword()));
        }
        //1.2 filter
        //分类
        if (param.getCatalog3Id() != null) {
            boolQuery.filter(QueryBuilders.termQuery("catalogId", param.getCatalog3Id()));
        }
        //品牌
        if (param.getBrandId() != null && param.getBrandId().size() > 0) {
            boolQuery.filter(QueryBuilders.termsQuery("brandId", param.getBrandId()));
        }
        //属性
        if (param.getAttrs() != null && param.getAttrs().size() > 0) {
            //嵌入式属性  设置不参与评分
            //attrs=1_5寸:8寸&attrs=2_5G:4G
            for (String attrStr : param.getAttrs()) {
                BoolQueryBuilder nestedBoolQuery = QueryBuilders.boolQuery();
                String[] s = attrStr.split("_");
                String attrId = s[0];
                String[] attrValues = s[1].split(":");
                nestedBoolQuery.must(QueryBuilders.termQuery("attrs.attrId", attrId));
                nestedBoolQuery.must(QueryBuilders.termsQuery("attrs.attrValue", attrValues));

                //包裹两个term聚合
                NestedQueryBuilder nestedQuery = QueryBuilders.nestedQuery("attrs", nestedBoolQuery, ScoreMode.None);
                //并入filter
                boolQuery.filter(nestedQuery);
            }

        }
        //库存
        boolQuery.filter(QueryBuilders.termQuery("hasStock", param.getHasStock()==1));
        //价格区间
        if (!StringUtils.isEmpty(param.getSkuPrice())) {
            //500_1000/500_/_1000
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("skuPrice");
            String[] s = param.getSkuPrice().split("_");
            if (param.getSkuPrice().startsWith("_")) {
                rangeQuery.lte(s[1]);
            } else if (param.getSkuPrice().endsWith("_")) {
                rangeQuery.gte(s[0]);
            } else {
                rangeQuery.gte(s[0]).lte(s[1]);
            }
            boolQuery.filter(rangeQuery);
        }

        //把整合的条件封装
        sourceBuilder.query(boolQuery);

        //排序，分页，高亮
        //排序
        if (!StringUtils.isEmpty(param.getSort())) {
            String[] s = param.getSort().split("_");
            sourceBuilder.sort(s[0], SortOrder.fromString(s[1]));
        }
        //分页 0开始
        //from = (pageNum - 1) * pageSize
        sourceBuilder.from((param.getPageNum() - 1) * EsConstant.PRODUCT_PAGESIZE);
        sourceBuilder.size(EsConstant.PRODUCT_PAGESIZE);

        //高亮
        if (!StringUtils.isEmpty(param.getKeyword())) {
            HighlightBuilder builder = new HighlightBuilder();
            builder.preTags("<b style='color:red'>");
            builder.field("skuTitle");
            builder.postTags("</b>");
            sourceBuilder.highlighter(builder);
        }

//        String s = sourceBuilder.toString();
//        System.out.println("构建的DSL\n"+s);

        //TODO 聚合分析
        //1.品牌ID聚合
        TermsAggregationBuilder brandAgg = AggregationBuilders.terms("brandAgg").field("brandId").size(50);

        //品牌名字子聚合、品牌图片子聚合
        TermsAggregationBuilder brandNameAgg = AggregationBuilders.terms("brandNameAgg").field("brandName").size(1);
        TermsAggregationBuilder brandImgAgg = AggregationBuilders.terms("brandImgAgg").field("brandImg");
        brandAgg.subAggregation(brandNameAgg);
        brandAgg.subAggregation(brandImgAgg);

        //整理聚合
        sourceBuilder.aggregation(brandAgg);

        //2.分类聚合
        TermsAggregationBuilder catalogAgg = AggregationBuilders.terms("catalogAgg").field("catalogId").size(20);

        //分类名称子聚合
        TermsAggregationBuilder catalogNameAgg = AggregationBuilders.terms("catalogNameAgg").field("catalogName");
        catalogAgg.subAggregation(catalogNameAgg);

        //整理聚合
        sourceBuilder.aggregation(catalogAgg);

        //3.嵌入式属性聚合
        NestedAggregationBuilder attrAgg = AggregationBuilders.nested("attrAgg", "attrs");

        //属性ID聚合
        TermsAggregationBuilder attrIdAgg = AggregationBuilders.terms("attrIdAgg").field("attrs.attrId");

        //属性名聚合
        TermsAggregationBuilder attrNameAgg = AggregationBuilders.terms("attrNameAgg").field("attrs.attrName").size(1);
        //属性值聚合
        TermsAggregationBuilder attrValueAgg = AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue");
        //设置属性ID的子聚合（属性名聚合、属性值聚合）
        attrIdAgg.subAggregation(attrNameAgg);
        attrIdAgg.subAggregation(attrValueAgg);
        //整合到嵌入式属性聚合
        attrAgg.subAggregation(attrIdAgg);

        //整理聚合
        sourceBuilder.aggregation(attrAgg);

        String s = sourceBuilder.toString();
        System.out.println("构建的DSL\n"+s);

        SearchRequest searchRequest = new SearchRequest(
                new String[]{EsConstant.PRODUCT_INDEX},
                sourceBuilder
        );
        return searchRequest;
    }
}
