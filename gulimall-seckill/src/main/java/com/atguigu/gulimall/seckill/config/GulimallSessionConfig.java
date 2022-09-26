package com.atguigu.gulimall.seckill.config;

import com.alibaba.fastjson.support.spring.GenericFastJsonRedisSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@EnableRedisHttpSession
@Configuration
public class GulimallSessionConfig {

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer cookieSerializer = new DefaultCookieSerializer();
        //指定作用域
        cookieSerializer.setDomainName("gulimall.com");
        cookieSerializer.setCookieName("GULI_SESSION");
        return cookieSerializer;
    }

    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        //序列化机制
//        ParserConfig.getGlobalInstance().setAutoTypeSupport(true);
        return new GenericFastJsonRedisSerializer();
//        return new GenericJackson2JsonRedisSerializer();
    }

}
