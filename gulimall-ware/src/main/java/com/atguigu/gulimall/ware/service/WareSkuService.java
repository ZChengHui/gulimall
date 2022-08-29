package com.atguigu.gulimall.ware.service;

import com.atguigu.gulimall.ware.vo.SkuHasStockVO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.gulimall.ware.entity.WareSkuEntity;

import java.util.List;
import java.util.Map;

/**
 * 商品库存
 *
 * @author zhangchenghui
 * @email 2783300744@qq.com
 * @date 2022-08-20 14:44:42
 */
public interface WareSkuService extends IService<WareSkuEntity> {

    PageUtils queryPage(Map<String, Object> params);

    void addStock(Long skuId, Long wareId, Integer skuNum);

    List<SkuHasStockVO> hasStock(List<Long> skuIds);
}

