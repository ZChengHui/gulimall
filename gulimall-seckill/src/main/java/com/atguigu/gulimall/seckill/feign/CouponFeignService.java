package com.atguigu.gulimall.seckill.feign;

import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient("gulimall-coupon")
public interface CouponFeignService {

    /**
     * 获取最近三天的秒杀活动
     */
    @GetMapping("/coupon/seckillsession/latest3DaysSession")
    R getLatest3DaysSession();

}
