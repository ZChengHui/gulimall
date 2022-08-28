package com.atguigu.gulimall.search;


import com.alibaba.fastjson.JSON;
import com.atguigu.gulimall.search.config.GulimallElasticSearchConfig;
import lombok.Data;
import lombok.ToString;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Avg;
import org.elasticsearch.search.aggregations.metrics.AvgAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

@SpringBootTest
@RunWith(SpringRunner.class)//指定启动引导
public class GulimallSearchApplicationTests {

    @Autowired
    private RestHighLevelClient client;

    /**
     * 测试检索数据
     */
    @Test
    public void search() throws IOException {
        //1.创建检索请求
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices("bank");//指定索引
        SearchSourceBuilder builder = new SearchSourceBuilder();

        //1.1构造检索条件
//        //DSL根操作都有
//        builder.query();
//        builder.from();
//        builder.aggregation();

//        QueryBuilders.boolQuery();
//        QueryBuilders.termQuery();
//        QueryBuilders.matchQuery();
//        QueryBuilders.multiMatchQuery();
//        QueryBuilders.matchPhraseQuery();

        builder.query(QueryBuilders.matchQuery("address","mill"));

        //年龄分布聚合
        TermsAggregationBuilder ageAgg = AggregationBuilders.terms("ageAgg").field("age").size(10);
        builder.aggregation(ageAgg);
        //平均薪资聚合
        AvgAggregationBuilder avgAgg = AggregationBuilders.avg("balAgg").field("balance");
        builder.aggregation(avgAgg);

        System.out.println("查询条件："+builder.toString());

        searchRequest.source(builder);

        //2.执行检索
        SearchResponse searchResponse = client.search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);

        //3.分析结果
        System.out.println("响应结果"+searchResponse.toString());
        //3.1 获取所有命中的记录
        SearchHits hits = searchResponse.getHits();
        SearchHit[] searchHits = hits.getHits();
        for (SearchHit hit : searchHits) {
            hit.getIndex();
            String sourceAsString = hit.getSourceAsString();
            Account account = JSON.parseObject(sourceAsString, Account.class);
            System.out.println(account);
        }
        //3.2 获取聚合
        Aggregations aggregations = searchResponse.getAggregations();

        Terms ageAgg1 = aggregations.get("ageAgg");
        for (Terms.Bucket bucket : ageAgg1.getBuckets()) {
            String keyAsString = bucket.getKeyAsString();
            System.out.println("年龄："+keyAsString);
        }

        Avg balAgg1 = aggregations.get("balAgg");
        System.out.println("平均薪资:"+balAgg1.getValue());
    }

    /**
     * 测试保存索引 插入文档
     * @throws IOException
     */
    @Test
    public void indexData() throws IOException {
        IndexRequest indexRequest = new IndexRequest("users");
        indexRequest.id("1");//索引的id
        User user = new User();
        user.setUserName("z");
        user.setAge(1);
        user.setGender("M");
        String jsonString = JSON.toJSONString(user);//转json保存数据
        indexRequest.source(jsonString, XContentType.JSON);

        //执行操作
        IndexResponse index = client.index(indexRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);

        System.out.println(index);
    }

    @ToString
    @Data
    static class Account{
            private int account_number;
            private int balance;
            private String firstname;
            private String lastname;
            private int age;
            private String gender;
            private String address;
            private String employer;
            private String email;
            private String city;
            private String state;
    }

    @Data
    class User{
        private String userName;
        private String gender;
        private Integer age;
    }

    @Test
    public void contextLoads() {
        System.out.println(client);
    }

}
