package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.product.entity.SkuImagesEntity;
import com.atguigu.gulimall.product.entity.SpuInfoDescEntity;
import com.atguigu.gulimall.product.feign.SeckillFeignService;
import com.atguigu.gulimall.product.service.*;
import com.atguigu.gulimall.product.vo.SeckillInfoVO;
import com.atguigu.gulimall.product.vo.SkuItemSaleAttrVO;
import com.atguigu.gulimall.product.vo.SkuItemVO;
import com.atguigu.gulimall.product.vo.SpuItemAttrGroupAttrVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.SkuInfoDao;
import com.atguigu.gulimall.product.entity.SkuInfoEntity;
import org.springframework.util.StringUtils;


@Service("skuInfoService")
public class SkuInfoServiceImpl extends ServiceImpl<SkuInfoDao, SkuInfoEntity> implements SkuInfoService {

    @Autowired
    SkuImagesService imagesService;

    @Autowired
    SpuInfoDescService infoDescService;

    @Autowired
    AttrGroupService attrGroupService;

    @Autowired
    SkuSaleAttrValueService skuSaleAttrValueService;

    @Autowired
    SeckillFeignService seckillFeignService;

    @Autowired
    ThreadPoolExecutor executor;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SkuInfoEntity> page = this.page(
                new Query<SkuInfoEntity>().getPage(params),
                new QueryWrapper<SkuInfoEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public void saveSkuInfo(SkuInfoEntity skuInfoEntity) {
        this.save(skuInfoEntity);
    }

    @Override
    public PageUtils queryPageByCondition(Map<String, Object> params) {
        /**
         * key:
         * catelogId: 0
         * brandId: 0
         * min: 0
         * max: 0
         */
        QueryWrapper<SkuInfoEntity> wrapper = new QueryWrapper<>();
        String key = (String) params.get("key");
        if (!StringUtils.isEmpty(key)) {
            wrapper.and(w -> {
                w.eq("sku_id", key).or().like("sku_name", key);
            });
        }

        String catelogId = (String) params.get("catelogId");
        if (!StringUtils.isEmpty(catelogId) && !"0".equalsIgnoreCase(catelogId)) {
            wrapper.eq("catalog_id", catelogId);
        }

        String brandId = (String) params.get("brandId");
        if (!StringUtils.isEmpty(brandId) && !"0".equalsIgnoreCase(brandId)) {
            wrapper.eq("brand_id", brandId);
        }

        String min = (String) params.get("min");
        if (!StringUtils.isEmpty(min)) {
            wrapper.ge("price", min);
        }

        String max = (String) params.get("max");
        if (!StringUtils.isEmpty(max) && !"0".equalsIgnoreCase(max)) {
            wrapper.le("price", max);
        }

        IPage<SkuInfoEntity> page = this.page(
                new Query<SkuInfoEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    @Override
    public List<SkuInfoEntity> getSkusBySpuId(Long spuId) {
        List<SkuInfoEntity> list = this.list(
                new QueryWrapper<SkuInfoEntity>()
                        .eq("spu_id", spuId)
        );
        return list;
    }

    //??????sku????????????
    @Override
    public SkuItemVO item(Long skuId) throws ExecutionException, InterruptedException {
        SkuItemVO skuItemVO = new SkuItemVO();

        //????????????
        CompletableFuture<SkuInfoEntity> infoFuture = CompletableFuture.supplyAsync(() -> {
            //sku???????????? pms_sku_info
            SkuInfoEntity infoEntity = getById(skuId);
            skuItemVO.setInfo(infoEntity);
            return infoEntity;
        }, executor);

        CompletableFuture<Void> saleAttrFuture = infoFuture.thenAcceptAsync((res) -> {
            //spu????????????
            List<SkuItemSaleAttrVO> saleAttrVOS = skuSaleAttrValueService.getSaleAttrsBySpuId(res.getSpuId());
            skuItemVO.setSaleAttr(saleAttrVOS);
        }, executor);

        CompletableFuture<Void> descFuture = infoFuture.thenAcceptAsync((res) -> {
            //spu??????
            SpuInfoDescEntity spuInfoDescEntity = infoDescService.getById(res.getSpuId());
            skuItemVO.setDesp(spuInfoDescEntity);
        }, executor);

        CompletableFuture<Void> baseAttrFuture = infoFuture.thenAcceptAsync((res) -> {
            //??????????????????
            List<SpuItemAttrGroupAttrVO> attrGroupVOS = attrGroupService.getAttrGroupWithAttrsBySpuId(res.getSpuId(), res.getCatalogId());
            skuItemVO.setGroupAttrs(attrGroupVOS);
        }, executor);

        //??????id???spuid
//        Long catalogId = infoEntity.getCatalogId();
//        Long spuId = infoEntity.getSpuId();

        CompletableFuture<Void> imgFuture = CompletableFuture.runAsync(() -> {
            //sku???????????? pms_sku_images
            List<SkuImagesEntity> images = imagesService.getImagesBySkuId(skuId);
            skuItemVO.setImages(images);
        }, executor);

        CompletableFuture<Void> seckillFuture = CompletableFuture.runAsync(() -> {
            //????????????sku????????????????????????
            R seckillInfo = seckillFeignService.getSkuSeckillInfo(skuId);
            if (seckillInfo.getCode() == 0) {
                SeckillInfoVO seckillInfoData = seckillInfo.getData("data", new TypeReference<SeckillInfoVO>() {
                });
                skuItemVO.setSeckillInfo(seckillInfoData);
            }
        }, executor);

        //????????????????????????
        CompletableFuture.allOf(saleAttrFuture, descFuture, baseAttrFuture, imgFuture, seckillFuture).get();

        return skuItemVO;
    }

}