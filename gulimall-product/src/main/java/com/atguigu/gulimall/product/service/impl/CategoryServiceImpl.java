package com.atguigu.gulimall.product.service.impl;

import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

//    @Autowired
//    CategoryDao categoryDao;

    @Autowired
    CategoryBrandRelationService categoryBrandRelationService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    //以树形结构查出所有分类
    @Override
    public List<CategoryEntity> listWithTree() {

        //1、查出所有分类
        //baseMapper就是CategoryDao，省去再注入
        List<CategoryEntity> entities = baseMapper.selectList(null);

        //2、组装成父子树形结构
        List<CategoryEntity> level1Menu = entities.stream().filter(categoryEntity ->
             categoryEntity.getParentCid().equals(0L)
        ).map((menu) -> {
            menu.setChildren(getChildren(menu, entities));
            return menu;
        }).sorted((menu1, menu2) -> {
            return (menu1.getSort()==null?0: menu1.getSort()) - (menu2.getSort()==null?0: menu2.getSort());
        }).collect(Collectors.toList());

        return level1Menu;
    }

    //删除分类
    @Override
    public void removeMenuByIds(List<Long> asList) {
        //TODO 检查引用
        //逻辑删除
        baseMapper.deleteBatchIds(asList);
    }

    //找到catelogId的完整路径
    @Override
    public Long[] findCatelogPath(Long catelogId) {
        List<Long> paths = new ArrayList<>();
        findParentPath(catelogId, paths);
        return paths.toArray(new Long[paths.size()]);
    }

    /**
     * 级联更新所有关联数据
     * @param category
     */
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        this.updateById(category);
        if (!StringUtils.isEmpty(category.getName())) {
            this.baseMapper.updateCategory(category.getCatId(), category.getName());
            //TODO 其他关联更新
        }
    }

    private void findParentPath(Long catelogId, List<Long> path) {
        CategoryEntity category = this.getById(catelogId);
        if (!category.getParentCid().equals(0L)) {
            findParentPath(category.getParentCid(), path);
        }
        path.add(catelogId);
        return;
    }

    //递归查找所有菜单子菜单
    private List<CategoryEntity> getChildren(CategoryEntity root, List<CategoryEntity> all) {

        List<CategoryEntity> children = all.stream().filter(categoryEntity -> {
            //找子菜单
            return categoryEntity.getParentCid().equals(root.getCatId());
        }).map(categoryEntity -> {
            //设置子菜单
            categoryEntity.setChildren(getChildren(categoryEntity, all));
            return categoryEntity;
        }).sorted((menu1, menu2) -> {
            //排序
            return (menu1.getSort()==null?0: menu1.getSort()) - (menu2.getSort()==null?0: menu2.getSort());
        }).collect(Collectors.toList());

        return children;
    }

}