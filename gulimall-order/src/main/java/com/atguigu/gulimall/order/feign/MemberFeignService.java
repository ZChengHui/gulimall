package com.atguigu.gulimall.order.feign;

import com.atguigu.gulimall.order.vo.MemberAddressVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.List;

@FeignClient("gulimall-member")
public interface MemberFeignService {

    //获取收货地址列表
    @GetMapping("/member/memberreceiveaddress/{memberId}/addresses")
    List<MemberAddressVO> getAddress(@PathVariable("memberId") Long memberId);

}
