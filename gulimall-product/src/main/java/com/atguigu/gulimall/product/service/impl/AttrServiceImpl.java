package com.atguigu.gulimall.product.service.impl;

import com.atguigu.common.constant.ProductConstant;
import com.atguigu.gulimall.product.dao.AttrAttrgroupRelationDao;
import com.atguigu.gulimall.product.dao.AttrGroupDao;
import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.AttrAttrgroupRelationEntity;
import com.atguigu.gulimall.product.entity.AttrGroupEntity;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.AttrResponseVO;
import com.atguigu.gulimall.product.vo.AttrVo;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.AttrDao;
import com.atguigu.gulimall.product.entity.AttrEntity;
import com.atguigu.gulimall.product.service.AttrService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service("attrService")
public class AttrServiceImpl extends ServiceImpl<AttrDao, AttrEntity> implements AttrService {

    @Autowired
    AttrAttrgroupRelationDao attrAttrgroupRelationDao;

    @Autowired
    CategoryDao categoryDao;

    @Autowired
    AttrGroupDao attrGroupDao;

    @Autowired
    CategoryService categoryService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<AttrEntity> page = this.page(
                new Query<AttrEntity>().getPage(params),
                new QueryWrapper<AttrEntity>()
        );

        return new PageUtils(page);
    }

    @Transactional
    @Override
    public void saveAttr(AttrVo attrVo) {
        //??????????????????
        AttrEntity attrEntity = new AttrEntity();
        BeanUtils.copyProperties(attrVo, attrEntity);
        this.save(attrEntity);

        //??????????????????
        //?????????????????????
        if (attrVo.getAttrType() == ProductConstant.AttrEnum.ATTR_TYPE_BASE.getCode() && attrVo.getAttrGroupId() != null) {
            AttrAttrgroupRelationEntity attrAttrgroupRelationEntity = new AttrAttrgroupRelationEntity();
            attrAttrgroupRelationEntity.setAttrGroupId(attrVo.getAttrGroupId());
            attrAttrgroupRelationEntity.setAttrId(attrEntity.getAttrId());
            attrAttrgroupRelationDao.insert(attrAttrgroupRelationEntity);
        }
        //?????????????????????
    }

    @Override
    public PageUtils queryBaseAttrPage(Map<String, Object> params, Long catelogId) {
        QueryWrapper<AttrEntity> wrapper = new QueryWrapper<>();

        String key = (String) params.get("key");
        if (!StringUtils.isEmpty(key)) {
            wrapper.and(obj -> {
                obj.eq("attr_id", key).or().like("attr_name", key);
            });
        }

        if (!catelogId.equals(0L)) {
            wrapper.eq("catelog_id", catelogId);
        }

        IPage<AttrEntity> page = this.page(
                new Query<AttrEntity>().getPage(params),
               wrapper
        );
        //????????? ???AttrEntity??????
        PageUtils pageUtils = new PageUtils(page);
        List<AttrEntity> records = page.getRecords();
        List<AttrResponseVO> attrResponseVOS = records.stream().map(attrEntity -> {
            AttrResponseVO attrResponseVO = new AttrResponseVO();
            BeanUtils.copyProperties(attrEntity, attrResponseVO);

            //?????????????????????????????????
            AttrAttrgroupRelationEntity attrAttrgroupRelationEntity = attrAttrgroupRelationDao.selectOne(
                    new QueryWrapper<AttrAttrgroupRelationEntity>()
                            .eq("attr_id", attrEntity.getAttrId()));
            if (attrAttrgroupRelationEntity != null && attrAttrgroupRelationEntity.getAttrGroupId() != null) {
                AttrGroupEntity attrGroupEntity = attrGroupDao.selectById(attrAttrgroupRelationEntity.getAttrGroupId());
                attrResponseVO.setGroupName(attrGroupEntity.getAttrGroupName());
            }
            CategoryEntity category = categoryDao.selectById(attrEntity.getCatelogId());
            if (category != null) {
                attrResponseVO.setCatelogName(category.getName());
            }

            return attrResponseVO;
        }).collect(Collectors.toList());

        //????????????
        pageUtils.setList(attrResponseVOS);
        return pageUtils;
    }

    @Override
    public AttrResponseVO getAttrInfo(Long attrId) {
        AttrEntity attrEntity = this.getById(attrId);
        AttrResponseVO attrResponseVO = new AttrResponseVO();
        BeanUtils.copyProperties(attrEntity, attrResponseVO);

        if (attrEntity.getAttrType() == ProductConstant.AttrEnum.ATTR_TYPE_BASE.getCode()) {
            //????????????
            AttrAttrgroupRelationEntity attrAttrgroupRelationEntity = attrAttrgroupRelationDao.selectOne(
                    new QueryWrapper<AttrAttrgroupRelationEntity>()
                            .eq("attr_id", attrId)
            );
            if (attrAttrgroupRelationEntity != null) {
                attrResponseVO.setAttrGroupId(attrAttrgroupRelationEntity.getAttrGroupId());
                AttrGroupEntity attrGroupEntity = attrGroupDao.selectById(attrAttrgroupRelationEntity.getAttrGroupId());
                if (attrGroupEntity != null) {
                    attrResponseVO.setGroupName(attrGroupEntity.getAttrGroupName());
                }
            }
        }

        //????????????
        Long[] catelogPath = categoryService.findCatelogPath(attrEntity.getCatelogId());
        attrResponseVO.setCatelogPath(catelogPath);
        CategoryEntity category = categoryDao.selectById(attrEntity.getCatelogId());
        if (category != null) {
            attrResponseVO.setCatelogName(category.getName());
        }
        return attrResponseVO;
    }

    @Transactional
    @Override
    public void updateAttr(AttrVo attr) {
        //??????attr
        AttrEntity attrEntity = new AttrEntity();
        BeanUtils.copyProperties(attr, attrEntity);
        this.updateById(attrEntity);

        if (attrEntity.getAttrType() == ProductConstant.AttrEnum.ATTR_TYPE_BASE.getCode()) {
            //??????relation
            //??????????????????????????????
            AttrAttrgroupRelationEntity attrAttrgroupRelationEntity = new AttrAttrgroupRelationEntity();
            attrAttrgroupRelationEntity.setAttrGroupId(attr.getAttrGroupId());
            attrAttrgroupRelationDao.update(
                    attrAttrgroupRelationEntity,
                    new UpdateWrapper<AttrAttrgroupRelationEntity>()
                            .eq("attr_id", attr.getAttrId())
            );
        }
    }

    @Override
    public PageUtils queryBaseAttrPage(Map<String, Object> params, Long catelogId, String type) {
        QueryWrapper<AttrEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("attr_type",
                "base".equalsIgnoreCase(type)?
                ProductConstant.AttrEnum.ATTR_TYPE_BASE.getCode():
                ProductConstant.AttrEnum.ATTR_TYPE_SALE.getCode());
        String key = (String) params.get("key");
        if (!StringUtils.isEmpty(key)) {
            wrapper.and(obj -> {
                obj.eq("attr_id", key).or().like("attr_name", key);
            });
        }

        if (!catelogId.equals(0L)) {
            wrapper.eq("catelog_id", catelogId);
        }

        IPage<AttrEntity> page = this.page(
                new Query<AttrEntity>().getPage(params),
                wrapper
        );
        //????????? ???AttrEntity??????
        PageUtils pageUtils = new PageUtils(page);
        List<AttrEntity> records = page.getRecords();
        List<AttrResponseVO> attrResponseVOS = records.stream().map(attrEntity -> {
            AttrResponseVO attrResponseVO = new AttrResponseVO();
            BeanUtils.copyProperties(attrEntity, attrResponseVO);

            //?????????????????????????????????
            //???????????????????????????
            if ("base".equalsIgnoreCase(type)) {
                AttrAttrgroupRelationEntity attrAttrgroupRelationEntity = attrAttrgroupRelationDao.selectOne(
                        new QueryWrapper<AttrAttrgroupRelationEntity>()
                                .eq("attr_id", attrEntity.getAttrId()));
                if (attrAttrgroupRelationEntity != null) {
                    AttrGroupEntity attrGroupEntity = attrGroupDao.selectById(attrAttrgroupRelationEntity.getAttrGroupId());
                    attrResponseVO.setGroupName(attrGroupEntity.getAttrGroupName());
                }
            }
            //??????????????????
            CategoryEntity category = categoryDao.selectById(attrEntity.getCatelogId());
            if (category != null) {
                attrResponseVO.setCatelogName(category.getName());
            }

            return attrResponseVO;
        }).collect(Collectors.toList());

        //????????????
        pageUtils.setList(attrResponseVOS);
        return pageUtils;
    }

    //????????????id???????????????????????????
    @Override
    public List<AttrEntity> getRelationAttr(Long attrgroupId) {
        List<AttrAttrgroupRelationEntity> relations = attrAttrgroupRelationDao.selectList(
                new QueryWrapper<AttrAttrgroupRelationEntity>()
                        .eq("attr_group_id", attrgroupId)
        );
        if (relations != null && relations.size() != 0) {
            List<Long> attrIds = relations.stream().map(attr -> {
                return attr.getAttrId();
            }).collect(Collectors.toList());

            Collection<AttrEntity> attrEntities = this.listByIds(attrIds);

            return (List<AttrEntity>) attrEntities;
        } else {
            return null;
        }
    }

    /**
     * ???????????????????????????????????????
     * @param params
     * @param attrGroupId
     * @return
     */
    @Override
    public PageUtils getNoRelationAttr(Map<String, Object> params, Long attrGroupId) {
        //1.????????????????????????????????????????????????
        AttrGroupEntity attrGroupEntity = attrGroupDao.selectById(attrGroupId);
        Long catelogId = attrGroupEntity.getCatelogId();
        //2.?????????????????????????????????????????????????????????
        //1).??????????????????????????????
        List<AttrGroupEntity> otherGroup = attrGroupDao.selectList(
                new QueryWrapper<AttrGroupEntity>()
                        .eq("catelog_id", catelogId)
        );
        List<Long> otherGroupIds = otherGroup.stream().map(item -> {
            return item.getAttrGroupId();
        }).collect(Collectors.toList());
        //2).???????????????????????????
        List<AttrAttrgroupRelationEntity> group = attrAttrgroupRelationDao.selectList(
                new QueryWrapper<AttrAttrgroupRelationEntity>()
                        .in("attr_group_id", otherGroupIds)
        );
        List<Long> attrIds = group.stream().map(item -> {
            return item.getAttrId();
        }).collect(Collectors.toList());



        //3).???????????????????????????????????????????????????
        QueryWrapper<AttrEntity> wrapper = new QueryWrapper<AttrEntity>();
        wrapper.eq("attr_type", ProductConstant.AttrEnum.ATTR_TYPE_BASE.getCode());
        wrapper.eq("catelog_id", catelogId);
        if (attrIds != null && attrIds.size() > 0) {
            wrapper.notIn("attr_id", attrIds);
        }

        String key = (String) params.get("key");
        if (!StringUtils.isEmpty(key)) {
            wrapper.and(obj -> {
                obj.eq("attr_id", key).or().like("attr_name", key);
            });
        }
        IPage<AttrEntity> page = this.page(
                new Query<AttrEntity>().getPage(params) ,
                wrapper);
        PageUtils pageUtils = new PageUtils(page);
        return pageUtils;
    }

    @Override
    public List<Long> selectSearchAttrs(List<Long> attrIds) {
        return baseMapper.selectSearchAttrIds(attrIds);
    }

}