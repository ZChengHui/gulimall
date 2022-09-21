package com.atguigu.gulimall.order.feign;

import com.atguigu.gulimall.order.vo.OrderItemVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient("gulimall-cart")
public interface CartFeignService {

    //获取当前用户所有购物项
    @GetMapping("/currentUserCartItems")
    List<OrderItemVO> getCurrentUserCartItems();

}
