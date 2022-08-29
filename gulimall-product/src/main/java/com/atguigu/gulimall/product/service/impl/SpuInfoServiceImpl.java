package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.ProductConstant;
import com.atguigu.common.to.SkuReductionTO;
import com.atguigu.common.to.SpuBoundTO;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.product.entity.*;
import com.atguigu.gulimall.product.feign.CouponFeignService;
import com.atguigu.gulimall.product.feign.SearchFeignService;
import com.atguigu.gulimall.product.feign.WareFeignService;
import com.atguigu.gulimall.product.service.*;
import com.atguigu.gulimall.product.vo.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.SpuInfoDao;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    SpuInfoDescService spuInfoDescService;

    @Autowired
    SpuImagesService spuImagesService;

    @Autowired
    AttrService attrService;

    @Autowired
    ProductAttrValueService attrValueService;

    @Autowired
    SkuInfoService skuInfoService;

    @Autowired
    SkuImagesService skuImagesService;

    @Autowired
    SkuSaleAttrValueService skuSaleAttrValueService;

    @Autowired
    ProductAttrValueService productAttrValueService;

    @Autowired
    CouponFeignService couponFeignService;

    @Autowired
    WareFeignService wareFeignService;

    @Autowired
    SearchFeignService searchFeignService;

    @Autowired
    BrandService brandService;

    @Autowired
    CategoryService categoryService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * //TODO 高级部分完善
     * @param vo
     */
    @Transactional
    @Override
    public void saveSpuInfo(SpuSaveVo vo) {

        //1、保存spu基本信息 pms_spu_info
        SpuInfoEntity infoEntity = new SpuInfoEntity();
        BeanUtils.copyProperties(vo, infoEntity);
        infoEntity.setCreateTime(new Date());
        infoEntity.setUpdateTime(new Date());
        this.saveBaseSpuInfo(infoEntity);

        //2、保存Spu的描述图片 pms_spu_info_desc
        List<String> desc = vo.getDecript();
        SpuInfoDescEntity spuInfoDescEntity = new SpuInfoDescEntity();
        //设置spuId
        spuInfoDescEntity.setSpuId(infoEntity.getId());
        spuInfoDescEntity.setDecript(String.join(",", desc));
        spuInfoDescService.saveSpuInfoDesc(spuInfoDescEntity);

        //3、保存spu的图片集 pms_spu_images
        List<String> images = vo.getImages();
        spuImagesService.saveImages(infoEntity.getId(), images);

        //4、保存spu的规格参数 pms_product_attr_value
        List<BaseAttrs> baseAttrs = vo.getBaseAttrs();
        List<ProductAttrValueEntity> attrValueEntities = baseAttrs.stream().map(attr -> {
            ProductAttrValueEntity attrValue = new ProductAttrValueEntity();
            attrValue.setAttrId(attr.getAttrId());
            AttrEntity byId = attrService.getById(attr.getAttrId());
            attrValue.setAttrName(byId.getAttrName());
            attrValue.setAttrValue(attr.getAttrValues());
            attrValue.setQuickShow(attr.getShowDesc());
            //设置spuId
            attrValue.setSpuId(infoEntity.getId());

            return attrValue;
        }).collect(Collectors.toList());
        productAttrValueService.saveProductAttr(attrValueEntities);


        // 保存spu的积分信息 gulimall_coupon -> sms_spu_bounds
        Bounds bounds = vo.getBounds();
        SpuBoundTO spuBoundTO = new SpuBoundTO();
        BeanUtils.copyProperties(bounds, spuBoundTO);
        //设置spuId
        spuBoundTO.setSpuId(infoEntity.getId());
        R r = couponFeignService.saveSpuBounds(spuBoundTO);
        if (r.getCode() != 0) {
            log.error("远程保存spu积分信息失败");
        }

        //5、保存当前spu对应的所有sku信息
        List<Skus> skusList = vo.getSkus();
        if (skusList != null && skusList.size() > 0) {
            skusList.forEach(item -> {
                //默认图片
                List<Images> itemImages = item.getImages();
                String defaultImg = "";
                for (Images image : itemImages) {
                    if (image.getDefaultImg() == 1) {
                        defaultImg = image.getImgUrl();
                    }
                }
                //5.1）、sku的基本信息 pms_sku_info
                SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
                /**
                 *     private String skuName;
                 *     private BigDecimal price;
                 *     private String skuTitle;
                 *     private String skuSubtitle;
                 */
                BeanUtils.copyProperties(item, skuInfoEntity);

                skuInfoEntity.setBrandId(infoEntity.getBrandId());
                skuInfoEntity.setCatalogId(infoEntity.getCatalogId());
                skuInfoEntity.setSaleCount(0L);
                //设置spuId
                skuInfoEntity.setSpuId(infoEntity.getId());
                skuInfoEntity.setSkuDefaultImg(defaultImg);
                skuInfoEntity.setSkuDesc(infoEntity.getSpuDescription());
                skuInfoService.saveSkuInfo(skuInfoEntity);

                Long skuId = skuInfoEntity.getSkuId();

                //5.2）、sku的图片信息 pms_sku_image
                List<SkuImagesEntity> imagesEntities = itemImages.stream().map(img -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setImgUrl(img.getImgUrl());
                    skuImagesEntity.setDefaultImg(img.getDefaultImg());

                    return skuImagesEntity;
                }).filter(entity -> {
                    //返回true需要，返回false过滤掉
                    return !StringUtils.isEmpty(entity.getImgUrl());
                }).collect(Collectors.toList());
                skuImagesService.saveBatch(imagesEntities);

                //5.3）、sku的销售属性信息 pms_sku_sale_attr_value
                List<Attr> attr = item.getAttr();
                List<SkuSaleAttrValueEntity> attrValueEntityList = attr.stream().map(a -> {
                    SkuSaleAttrValueEntity attrValue = new SkuSaleAttrValueEntity();
                    BeanUtils.copyProperties(a, attrValue);
                    attrValue.setSkuId(skuId);
                    return attrValue;
                }).collect(Collectors.toList());
                skuSaleAttrValueService.saveBatch(attrValueEntityList);
                
                // //5.4)、sku的优惠、满减等信息
                SkuReductionTO skuReductionTO = new SkuReductionTO();
                BeanUtils.copyProperties(item, skuReductionTO);
                skuReductionTO.setSkuId(skuId);
                //compareTo 比大小
                if (skuReductionTO.getFullCount() > 0 || skuReductionTO.getFullPrice().compareTo(new BigDecimal("0")) == 1) {
                    R r1 = couponFeignService.saveSkuReduction(skuReductionTO);
                    if (r1.getCode() != 0) {
                        log.error("远程保存spu优惠信息失败");
                    }
                }

            });
        }

    }

    @Override
    public void saveBaseSpuInfo(SpuInfoEntity infoEntity) {
        this.baseMapper.insert(infoEntity);
    }

    @Override
    public PageUtils queryPageByCondition(Map<String, Object> params) {

        QueryWrapper<SpuInfoEntity> wrapper = new QueryWrapper<>();

        String key = (String) params.get("key");
        if(!StringUtils.isEmpty(key)){
            wrapper.and((w)->{
                w.eq("id",key).or().like("spu_name",key);
            });
        }
        // status=1 and (id=1 or spu_name like xxx)
        String status = (String) params.get("status");
        if(!StringUtils.isEmpty(status)){
            wrapper.eq("publish_status",status);
        }

        String brandId = (String) params.get("brandId");
        if(!StringUtils.isEmpty(brandId) && !"0".equalsIgnoreCase(brandId)){
            wrapper.eq("brand_id",brandId);
        }

        String catelogId = (String) params.get("catelogId");
        if(!StringUtils.isEmpty(catelogId) && !"0".equalsIgnoreCase(catelogId)){
            wrapper.eq("catalog_id",catelogId);
        }

        /**
         * status: 2
         * key:
         * brandId: 9
         * catelogId: 225
         */

        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    /**
     * 商品上架
     * @param spuId
     */
    @Override
    public void up(Long spuId) {
        //TODO 脏数据 空指针 处理


        //1.查出当前spuId对应的所有sku信息，品牌名字
        List<SkuInfoEntity> skuInfoEntities = skuInfoService.getSkusBySpuId(spuId);
        List<Long> skuIdList = skuInfoEntities.stream().map(SkuInfoEntity::getSkuId).collect(Collectors.toList());

        //TODO 查询spu可以被用来给es检索的属性
        List<ProductAttrValueEntity> productAttrValueEntities = attrValueService.baseAttrListForSpu(spuId);

        List<Long> attrIds = productAttrValueEntities.stream().map(item -> {
            return item.getAttrId();
        }).collect(Collectors.toList());

        List<Long> searchAttrIds = attrService.selectSearchAttrs(attrIds);

        Set<Long> idSet = new HashSet<>(searchAttrIds);

        //过滤 转换 收集
        List<SkuEsModel.Attrs> attrsList = productAttrValueEntities.stream().filter(item -> {
            return idSet.contains(item.getAttrId());
        }).map(item -> {
            SkuEsModel.Attrs attrs1 = new SkuEsModel.Attrs();
            BeanUtils.copyProperties(item, attrs1);
            return attrs1;
        }).collect(Collectors.toList());

        //TODO 发送远程调用，是否有库存
        /**
         * 返回参数问题
         * 1.R加上泛型
         * 2.直接返回list
         * 3.自己封装结果
         * 4.VO复制给其他微服务，属性名一样即可
         */
        Map<Long, Boolean> stockMap = null;
        try {
            R r = wareFeignService.getSkuHasStock(skuIdList);
            //TODO 获取data失败
            //复杂类型转换
            TypeReference<List<SkuHasStockVO>> typeReference = new TypeReference<List<SkuHasStockVO>>(){};
            stockMap = r.getData(typeReference).stream().collect(Collectors.toMap(SkuHasStockVO::getSkuId, SkuHasStockVO::getHasStock));
        } catch (Exception e) {
            log.error("库存查询异常:"+e);
        }

        //2.封装sku信息
        Map<Long, Boolean> finalStockMap = stockMap;
        List<SkuEsModel> upProducts = skuInfoEntities.stream().map(sku -> {
            //组装需要的数据
            SkuEsModel skuEsModel = new SkuEsModel();
            /*单独处理属性名不一致的属性
            private BigDecimal skuPrice;
            private String skuImg;
            private Boolean hasStock;
            private Long hotScore;
            private String brandName;
            private String brandImg;
            private String catalogName;
            public static class Attrs{
                private Long attrId;
                private String attrName;
                private String attrValue;
             }
             */
            skuEsModel.setSkuPrice(sku.getPrice());
            skuEsModel.setSkuImg(sku.getSkuDefaultImg());

            //设置库存信息
            if (finalStockMap == null) {
                skuEsModel.setHasStock(true);
            } else {
                skuEsModel.setHasStock(finalStockMap.get(sku.getSkuId()));
            }

            //TODO 热度评分
            skuEsModel.setHotScore(0L);

            //TODO 品牌名字、品牌图片、分类名字
            BrandEntity brand = brandService.getById(sku.getBrandId());
            skuEsModel.setBrandName(brand.getName());
            skuEsModel.setBrandImg(brand.getLogo());
            CategoryEntity category = categoryService.getById(sku.getCatalogId());
            skuEsModel.setCatalogName(category.getName());

            //设置符合的属性集
            skuEsModel.setAttrs(attrsList);
            BeanUtils.copyProperties(sku, skuEsModel);

            return skuEsModel;
        }).collect(Collectors.toList());

        //TODO 发送数据给es
        R r =searchFeignService.productStatusUp(upProducts);
        if (r.getCode() == 0) {
            //成功
            //TODO 修改商品spu发布状态
            this.baseMapper.updateSpuStatus(spuId, ProductConstant.StatusEnum.SPU_UP.getCode());
        } else {
            //远程调用失败
            //TODO 重复调用问题 接口幂等性 重试机制
        }

    }


}