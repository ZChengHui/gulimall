package com.atguigu.gulimall.product.feign;

import com.atguigu.common.to.SkuReductionTO;
import com.atguigu.common.to.SpuBoundTO;
import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("gulimall-gateway")
public interface CouponFeignService {

    /**
     * 类参数名一致即可json互转
     *
     * 1.给网关发请求 要加上完整路径
     *      /api/coupon/coupon/spubounds/save
     * 2.给远程服务发请求 用相对路径
     *      /coupon/spubounds/save
     *
     * @param spuBoundTO
     * @return
     */
    @PostMapping("/api/coupon/coupon/spubounds/save")
    R saveSpuBounds(@RequestBody SpuBoundTO spuBoundTO);

    //满减
    @PostMapping("/api/coupon/coupon/skufullreduction/saveinfo")
    R saveSkuReduction(@RequestBody SkuReductionTO skuReductionTO);
}
