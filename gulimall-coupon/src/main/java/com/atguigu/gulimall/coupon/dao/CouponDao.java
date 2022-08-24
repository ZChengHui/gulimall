package com.atguigu.gulimall.coupon.dao;

import com.atguigu.gulimall.coupon.entity.CouponEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券信息
 * 
 * @author zhangchenghui
 * @email 2783300744@qq.com
 * @date 2022-08-20 14:09:24
 */
@Mapper
public interface CouponDao extends BaseMapper<CouponEntity> {
	
}
