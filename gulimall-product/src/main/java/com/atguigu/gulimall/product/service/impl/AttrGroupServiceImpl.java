package com.atguigu.gulimall.product.service.impl;

import com.atguigu.gulimall.product.entity.AttrEntity;
import com.atguigu.gulimall.product.service.AttrAttrgroupRelationService;
import com.atguigu.gulimall.product.service.AttrService;
import com.atguigu.gulimall.product.vo.AttrGroupWithAttrsVO;
import com.atguigu.gulimall.product.vo.SkuItemVO;
import com.atguigu.gulimall.product.vo.SpuItemAttrGroupAttrVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.AttrGroupDao;
import com.atguigu.gulimall.product.entity.AttrGroupEntity;
import com.atguigu.gulimall.product.service.AttrGroupService;
import org.springframework.util.StringUtils;


@Service("attrGroupService")
public class AttrGroupServiceImpl extends ServiceImpl<AttrGroupDao, AttrGroupEntity> implements AttrGroupService {

    @Autowired
    private AttrAttrgroupRelationService attrAttrgroupRelationService;

    @Autowired
    private AttrService attrService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<AttrGroupEntity> page = this.page(
                new Query<AttrGroupEntity>().getPage(params),
                new QueryWrapper<AttrGroupEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public PageUtils queryPage(Map<String, Object> params, Long catelogId) {

        String key = (String) params.get("key");
        //select * from pms_attr_group where catelog_id = ? and (attr_group_id = key or attr_group_name = key)
        QueryWrapper<AttrGroupEntity> wrapper = new QueryWrapper<>();

        if (!StringUtils.isEmpty(key)) {
            //?????????????????????
            wrapper.and(obj -> {
                obj.eq("attr_group_id", key).or().like("attr_group_name", key);
            });
        }

        if (!catelogId.equals(0L)) {
            wrapper.eq("catelog_id", catelogId);
        }
        IPage<AttrGroupEntity> page = this.page(
                new Query<AttrGroupEntity>().getPage(params),
                wrapper);
        return new PageUtils(page);
    }

    @Override
    public List<AttrGroupWithAttrsVO> getAttrGroupWithAttrsByCatelogId(Long catelogId) {
        List<AttrGroupEntity> group = this.list(
                new QueryWrapper<AttrGroupEntity>()
                        .eq("catelog_id", catelogId)
        );

        List<AttrGroupWithAttrsVO> attrGroupWithAttrsVOS = group.stream().map(item -> {
            AttrGroupWithAttrsVO attrGroupWithAttrsVO = new AttrGroupWithAttrsVO();
            BeanUtils.copyProperties(item, attrGroupWithAttrsVO);

            List<AttrEntity> attrEntities = attrService.getRelationAttr(attrGroupWithAttrsVO.getAttrGroupId());
            attrGroupWithAttrsVO.setAttrs(attrEntities);

            return attrGroupWithAttrsVO;
        }).collect(Collectors.toList());

        return attrGroupWithAttrsVOS;
    }

    @Override
    public List<SpuItemAttrGroupAttrVO> getAttrGroupWithAttrsBySpuId(Long spuId, Long catalogId) {
        /**
         * ??????????????????spu??????????????????????????????????????????
         * SELECT
         * 	ag.attr_group_id,
         * 	ag.attr_group_name,
         * 	aar.attr_id,
         * 	attr.attr_name,
         * 	pav.attr_value
         * FROM
         * 	pms_attr_group ag
         * 	LEFT JOIN pms_attr_attrgroup_relation aar ON aar.attr_group_id = ag.attr_group_id
         * 	LEFT JOIN pms_attr attr ON attr.attr_id = aar.attr_id
         * 	LEFT JOIN pms_product_attr_value pav ON pav.attr_id = attr.attr_id
         * WHERE
         * 	ag.catelog_id = 225
         * 	AND pav.spu_id = 18
         */
        AttrGroupDao attrGroupDao = this.baseMapper;
        List<SpuItemAttrGroupAttrVO> vos = attrGroupDao.getAttrGroupWithAttrsBySpuId(spuId, catalogId);
        return vos;
    }

}