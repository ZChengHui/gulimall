package com.atguigu.gulimall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.gulimall.search.EsConstant;
import com.atguigu.gulimall.search.config.GulimallElasticSearchConfig;
import com.atguigu.gulimall.search.service.MallSearchService;
import com.atguigu.gulimall.search.vo.SearchParamVO;
import com.atguigu.gulimall.search.vo.SearchResultVO;

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
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MallSearchServiceImpl implements MallSearchService {

    @Autowired
    private RestHighLevelClient client;

    /**
     * @param param ES????????????
     * @return ???????????????????????????????????????
     */
    @Override
    public SearchResultVO search(SearchParamVO param) {
        //????????????DSL??????

        //??????????????????
        SearchRequest searchRequest = buildSearchRequest(param);
        SearchResultVO resultVO = null;
        try {
            //??????????????????
            SearchResponse response = client.search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);

            //?????????????????? ?????????VO
            resultVO = buildSearchResult(response, param);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return resultVO;
    }

    private SearchResultVO buildSearchResult(SearchResponse response, SearchParamVO param) {
        SearchResultVO resultVO = new SearchResultVO();
        SearchHits hits = response.getHits();

        List<SkuEsModel> esModels = new ArrayList<>();
        if (hits.getHits() != null && hits.getHits().length > 0) {
            for (SearchHit hit : hits) {
                String sourceAsString = hit.getSourceAsString();
                SkuEsModel esModel = JSON.parseObject(sourceAsString, SkuEsModel.class);
                //????????????skuTitle
                if (!StringUtils.isEmpty(param.getKeyword())) {
                    HighlightField skuTitle = hit.getHighlightFields().get("skuTitle");
                    String highlight = skuTitle.getFragments()[0].string();
                    esModel.setSkuTitle(highlight);
                }
                esModels.add(esModel);
            }
        }

        //????????????????????????
        resultVO.setProducts(esModels);

        //????????????????????????????????????
        ParsedLongTerms catalogAgg = response.getAggregations().get("catalogAgg");
        List<SearchResultVO.CatalogVO> catalogVOS = new ArrayList<>();
        //????????????????????????
        List<? extends Terms.Bucket> catalogAggBuckets = catalogAgg.getBuckets();
        catalogAggBuckets.forEach(bucket -> {
            SearchResultVO.CatalogVO catalogVO = new SearchResultVO.CatalogVO();
            //??????????????????ID
            Long catalogId = bucket.getKeyAsNumber().longValue();
            catalogVO.setCatalogId(catalogId);

            //???????????????????????????
            ParsedStringTerms catalogNameAgg = bucket.getAggregations().get("catalogNameAgg");
            //?????????????????????????????????????????????
            String catalogName = catalogNameAgg.getBuckets().get(0).getKeyAsString();
            catalogVO.setCatalogName(catalogName);

            catalogVOS.add(catalogVO);
        });

        resultVO.setCatalogs(catalogVOS);

        //????????????????????????????????????
        List<SearchResultVO.BrandVO> brandVOS = new ArrayList<>();
        ParsedLongTerms brandAgg = response.getAggregations().get("brandAgg");
        List<? extends Terms.Bucket> brandAggBuckets = brandAgg.getBuckets();
        brandAggBuckets.forEach(bucket -> {
            SearchResultVO.BrandVO brandVO = new SearchResultVO.BrandVO();
            //??????ID
            Long brandId = bucket.getKeyAsNumber().longValue();
            brandVO.setBrandId(brandId);

            ParsedStringTerms brandImgAgg = bucket.getAggregations().get("brandImgAgg");
            //????????????
            String brandImg = brandImgAgg.getBuckets().get(0).getKeyAsString();
            brandVO.setBrandImg(brandImg);

            ParsedStringTerms brandValueAgg = bucket.getAggregations().get("brandNameAgg");
            //?????????
            String brandName = brandValueAgg.getBuckets().get(0).getKeyAsString();
            brandVO.setBrandName(brandName);

            brandVOS.add(brandVO);
        });

        resultVO.setBrands(brandVOS);

        //????????????????????????????????????
        List<SearchResultVO.AttrVO> attrVOS = new ArrayList<>();
        ParsedNested attrAgg = response.getAggregations().get("attrAgg");
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> attrIdAggBuckets = attrIdAgg.getBuckets();
        attrIdAggBuckets.forEach(bucket -> {
            SearchResultVO.AttrVO attrVO = new SearchResultVO.AttrVO();
            //??????ID
            Long attrId = bucket.getKeyAsNumber().longValue();
            attrVO.setAttrId(attrId);
            //?????????
            ParsedStringTerms attrNameAgg = bucket.getAggregations().get("attrNameAgg");
            String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
            attrVO.setAttrName(attrName);
            //???????????????
            ParsedStringTerms attrValueAgg = bucket.getAggregations().get("attrValueAgg");
            List<String> attrValue = attrValueAgg.getBuckets().stream().map(item -> {
                String attrItemValue = item.getKeyAsString();
                return attrItemValue;
            }).collect(Collectors.toList());
            attrVO.setAttrValue(attrValue);

            attrVOS.add(attrVO);
        });

        resultVO.setAttrs(attrVOS);

        //????????????
        Long total = hits.getTotalHits().value;
        Integer totalPage = (int)((total + EsConstant.PRODUCT_PAGESIZE - 1) / EsConstant.PRODUCT_PAGESIZE);
        resultVO.setPageNum(param.getPageNum());
        resultVO.setTotal(total);
        resultVO.setTotalPage(totalPage);

        //?????????????????????
        List<Integer> pageNav = new ArrayList<>();
        for (int i = 1; i <= totalPage; i++) {
            pageNav.add(i);
        }
        resultVO.setPageNav(pageNav);

        //?????????
        if (!CollectionUtils.isEmpty(param.getAttrs())) {
            List<SearchResultVO.NavVO> navVOS = param.getAttrs().stream().map(attr -> {
                SearchResultVO.NavVO navVO = new SearchResultVO.NavVO();
                //??????????????????????????????attrs????????????
                String[] s = attr.split("_");
                navVO.setNavValue(s[1]);
                //?????????????????????????????????????????????ID???????????????
                Map<Long, String> map = attrVOS.stream().collect(Collectors.toMap(
                        k -> k.getAttrId(), v -> v.getAttrName()
                ));
                navVO.setNavName(map.get(Long.parseLong(s[0])));
                //??????????????????????????? ??????????????????URL??????
                //
                String encode = null;
                try {
                    encode = URLEncoder.encode(attr, "UTF-8");
                    //???????????????????????????java?????????
                    encode.replace("+", "%20");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                String replace = param.get_queryString().replace("&attrs=" + encode, "");
                //?????????
                navVO.setLink("http://search.gulimall.com/list.html?" + replace);
                return navVO;
            }).collect(Collectors.toList());

            resultVO.setNavs(navVOS);
        }

        return resultVO;
    }

    /**??????????????????
     * ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     * @return
     */
    private SearchRequest buildSearchRequest(SearchParamVO param) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        //???????????????????????????????????????????????????????????????????????????
        //1. bool query
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        //1.1 must
        if (!StringUtils.isEmpty(param.getKeyword())) {
            //match ????????????
            boolQuery.must(QueryBuilders.matchQuery("skuTitle", param.getKeyword()));
        }
        //1.2 filter
        //??????
        if (param.getCatalog3Id() != null) {
            boolQuery.filter(QueryBuilders.termQuery("catalogId", param.getCatalog3Id()));
        }
        //??????
        if (param.getBrandId() != null && param.getBrandId().size() > 0) {
            boolQuery.filter(QueryBuilders.termsQuery("brandId", param.getBrandId()));
        }
        //??????
        if (param.getAttrs() != null && param.getAttrs().size() > 0) {
            //???????????????  ?????????????????????
            //attrs=1_5???:8???&attrs=2_5G:4G
            for (String attrStr : param.getAttrs()) {
                BoolQueryBuilder nestedBoolQuery = QueryBuilders.boolQuery();
                String[] s = attrStr.split("_");
                String attrId = s[0];
                String[] attrValues = s[1].split(":");
                nestedBoolQuery.must(QueryBuilders.termQuery("attrs.attrId", attrId));
                nestedBoolQuery.must(QueryBuilders.termsQuery("attrs.attrValue", attrValues));

                //????????????term??????
                NestedQueryBuilder nestedQuery = QueryBuilders.nestedQuery("attrs", nestedBoolQuery, ScoreMode.None);
                //??????filter
                boolQuery.filter(nestedQuery);
            }

        }
        //??????
        if (param.getHasStock() != null) {
            boolQuery.filter(QueryBuilders.termQuery("hasStock", param.getHasStock() == 1));
        }
        //????????????
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

        //????????????????????????
        sourceBuilder.query(boolQuery);

        //????????????????????????
        //??????
        if (!StringUtils.isEmpty(param.getSort())) {
            String[] s = param.getSort().split("_");
            sourceBuilder.sort(s[0], SortOrder.fromString(s[1]));
        }
        //?????? 0??????
        //from = (pageNum - 1) * pageSize
        sourceBuilder.from((param.getPageNum() - 1) * EsConstant.PRODUCT_PAGESIZE);
        sourceBuilder.size(EsConstant.PRODUCT_PAGESIZE);

        //??????
        if (!StringUtils.isEmpty(param.getKeyword())) {
            HighlightBuilder builder = new HighlightBuilder();
            builder.preTags("<b style='color:red'>");
            builder.field("skuTitle");
            builder.postTags("</b>");
            sourceBuilder.highlighter(builder);
        }

//        String s = sourceBuilder.toString();
//        System.out.println("?????????DSL\n"+s);

        //TODO ????????????
        //1.??????ID??????
        TermsAggregationBuilder brandAgg = AggregationBuilders.terms("brandAgg").field("brandId").size(50);

        //?????????????????????????????????????????????
        TermsAggregationBuilder brandNameAgg = AggregationBuilders.terms("brandNameAgg").field("brandName").size(1);
        TermsAggregationBuilder brandImgAgg = AggregationBuilders.terms("brandImgAgg").field("brandImg");
        brandAgg.subAggregation(brandNameAgg);
        brandAgg.subAggregation(brandImgAgg);

        //????????????
        sourceBuilder.aggregation(brandAgg);

        //2.????????????
        TermsAggregationBuilder catalogAgg = AggregationBuilders.terms("catalogAgg").field("catalogId").size(20);

        //?????????????????????
        TermsAggregationBuilder catalogNameAgg = AggregationBuilders.terms("catalogNameAgg").field("catalogName");
        catalogAgg.subAggregation(catalogNameAgg);

        //????????????
        sourceBuilder.aggregation(catalogAgg);

        //3.?????????????????????
        NestedAggregationBuilder attrAgg = AggregationBuilders.nested("attrAgg", "attrs");

        //??????ID??????
        TermsAggregationBuilder attrIdAgg = AggregationBuilders.terms("attrIdAgg").field("attrs.attrId");

        //???????????????
        TermsAggregationBuilder attrNameAgg = AggregationBuilders.terms("attrNameAgg").field("attrs.attrName").size(1);
        //???????????????
        TermsAggregationBuilder attrValueAgg = AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue");
        //????????????ID???????????????????????????????????????????????????
        attrIdAgg.subAggregation(attrNameAgg);
        attrIdAgg.subAggregation(attrValueAgg);
        //??????????????????????????????
        attrAgg.subAggregation(attrIdAgg);

        //????????????
        sourceBuilder.aggregation(attrAgg);

        String s = sourceBuilder.toString();
        System.out.println("?????????DSL\n"+s);

        SearchRequest searchRequest = new SearchRequest(
                new String[]{EsConstant.PRODUCT_INDEX},
                sourceBuilder
        );
        return searchRequest;
    }
}
