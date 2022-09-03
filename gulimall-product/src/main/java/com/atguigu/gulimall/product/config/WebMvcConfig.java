package com.atguigu.gulimall.product.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

//改用nginx部署前端静态资源
//@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 配置thymeleaf
     * 映射静态资源
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//        registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");

    }
}
