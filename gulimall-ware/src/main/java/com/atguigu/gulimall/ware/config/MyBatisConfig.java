package com.atguigu.gulimall.ware.config;

import com.baomidou.mybatisplus.extension.plugins.PaginationInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;


@Configuration
@EnableTransactionManagement
@MapperScan("com.atguigu.gulimall.ware.dao")
public class MyBatisConfig {

    //分页插件
    @Bean
    public PaginationInterceptor paginationInterceptor() {
        PaginationInterceptor paginationInterceptor = new PaginationInterceptor();
        //设置请求页面大于最大页后操作 true回到首页 false继续请求
        paginationInterceptor.setOverflow(true);
        //设置最大单页限制数量
        paginationInterceptor.setLimit(1000);
        return paginationInterceptor;
    }


}
