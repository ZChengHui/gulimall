package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import com.atguigu.gulimall.product.vo.Catelog2VO;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Autowired
    StringRedisTemplate redis;
    
    @Autowired
    RedissonClient redisson;

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
            return (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort());
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
     *
     * @param category
     */
    //多次操作缓存
//    @Caching(evict = {
//            @CacheEvict(value = "category", key = "'getLevel1Categorys'"),
//            @CacheEvict(value = "category", key = "'getCatalogJson'")
//    })
    //删除分区下所有缓存，失效模式
    @CacheEvict(value = "category", allEntries = true)
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        this.updateById(category);
        if (!StringUtils.isEmpty(category.getName())) {
            this.baseMapper.updateCategory(category.getCatId(), category.getName());
            //TODO 其他关联更新
        }
    }

    //常规业务用本地锁，特殊情况加分布式锁
    @Cacheable(value = {"category"}, key = "#root.method.name", sync = true)//缓存分区名会被前缀配置覆盖，SpEL表达式作为名字
    @Override
    public List<CategoryEntity> getLevel1Categorys() {
        List<CategoryEntity> categoryEntities = baseMapper.selectList(
                new QueryWrapper<CategoryEntity>()
                        .eq("parent_cid", 0)
        );
        return categoryEntities;
    }

    //SpringCache版
    @Cacheable(value = "category", key = "#root.methodName")
    @Override
    public Map<String, List<Catelog2VO>> getCatalogJson() {

        List<CategoryEntity> selectList = baseMapper.selectList(null);
        //查出所有一级分类
        List<CategoryEntity> level1Categorys = getParent_cid(selectList, 0L);
        Map<String, List<Catelog2VO>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            //查二级分类
            List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
            List<Catelog2VO> catelog2VOS = null;
            if (categoryEntities != null) {
                catelog2VOS = categoryEntities.stream().map(l2 -> {
                    Catelog2VO catelog2VO = new Catelog2VO(
                            v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName()
                    );
                    //找三级分类并封装
                    List<CategoryEntity> level3Catelog = getParent_cid(selectList, l2.getCatId());
                    if (level3Catelog != null) {
                        List<Catelog2VO.Catelog3VO> collect = level3Catelog.stream().map(l3 -> {
                            Catelog2VO.Catelog3VO catelog3VO = new Catelog2VO.Catelog3VO(
                                    l2.getCatId().toString(), l3.getCatId().toString(), l3.getName()
                            );
                            return catelog3VO;
                        }).collect(Collectors.toList());
                        catelog2VO.setCatalog3List(collect);
                    }

                    return catelog2VO;
                }).collect(Collectors.toList());
            }
            return catelog2VOS;
        }));

        //更新缓存，返回结果
        return parent_cid;
    }

    //TODO 堆外内存溢出 OutOfDirectMemoryError 没有及时释放内存
    //换用jedis

    /**
     * 使用redis优化三级分类查询
     *
     * @return
     */
//    @Override
    public Map<String, List<Catelog2VO>> getCatalogJsonOld() {
        /**
         * 缓存穿透、缓存雪崩、缓存击穿
         * 解决方案：空结果缓存、设置随机过期时间、分布式锁
         */
        //两次检验
        //加入缓存逻辑
        String catalogJSON = redis.opsForValue().get("catalogJSON");
        if (StringUtils.isEmpty(catalogJSON)) {
            System.out.println("缓存不命中");
            //缓存中没有，查数据库
            Map<String, List<Catelog2VO>> catalogJsonFromDB = getCatalogJsonFromDBWithRedisLock();
            return catalogJsonFromDB;
        }
        System.out.println("命中返回");
        //转为指定对象
        Map<String, List<Catelog2VO>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catelog2VO>>>() {
        });
        return result;
    }

    /**
     * Redisson 分布式锁
     * 缓存数据一致性解决：失效模式+读写锁 访问度不大
     * 另一种，canal+binlog 缓存从数据库
     * @return
     */
    public Map<String, List<Catelog2VO>> getCatalogJsonFromDBWithRedissonLock() {
        RLock lock = redisson.getLock("catalogJson-lock");
        lock.lock();
        Map<String, List<Catelog2VO>> dataFromDb;
        try {
            dataFromDb = getDataFromDb();
        } finally {
            lock.unlock();
        }
        return dataFromDb;
    }

    /**
     * Redis 分布式锁
     *
     * @return
     */
    public Map<String, List<Catelog2VO>> getCatalogJsonFromDBWithRedisLock() {

        //占分布式锁
        //设置过期时间
        String uuid = UUID.randomUUID().toString();
        Boolean lock = redis.opsForValue().setIfAbsent("lock", uuid, 300, TimeUnit.SECONDS);
        if (lock) {
            //加锁成功，执行业务
            Map<String, List<Catelog2VO>> dataFromDb = getDataFromDb();
            //执行成功，解锁，只接自己的锁
            String lockValue = redis.opsForValue().get("lock");
            if (uuid.equals(lockValue)) {
                redis.delete("lock");
            }
//            redis.delete("lock");
            return dataFromDb;
        } else {
            //加锁失败，重试自旋
            //休眠100ms
            return getCatalogJsonFromDBWithRedisLock();
        }

    }

    //抽取成方法
    private Map<String, List<Catelog2VO>> getDataFromDb() {
        String catalogJSON = redis.opsForValue().get("catalogJSON");
        if (!StringUtils.isEmpty(catalogJSON)) {
            //命中直接返回
            Map<String, List<Catelog2VO>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catelog2VO>>>() {
            });
            return result;
        }

        List<CategoryEntity> selectList = baseMapper.selectList(null);

        //查出所有一级分类
        List<CategoryEntity> level1Categorys = getParent_cid(selectList, 0L);
        Map<String, List<Catelog2VO>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            //查二级分类
            List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
            List<Catelog2VO> catelog2VOS = null;
            if (categoryEntities != null) {
                catelog2VOS = categoryEntities.stream().map(l2 -> {
                    Catelog2VO catelog2VO = new Catelog2VO(
                            v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName()
                    );
                    //找三级分类并封装
                    List<CategoryEntity> level3Catelog = getParent_cid(selectList, l2.getCatId());
                    if (level3Catelog != null) {
                        List<Catelog2VO.Catelog3VO> collect = level3Catelog.stream().map(l3 -> {
                            Catelog2VO.Catelog3VO catelog3VO = new Catelog2VO.Catelog3VO(
                                    l2.getCatId().toString(), l3.getCatId().toString(), l3.getName()
                            );
                            return catelog3VO;
                        }).collect(Collectors.toList());
                        catelog2VO.setCatalog3List(collect);
                    }

                    return catelog2VO;
                }).collect(Collectors.toList());
            }
            return catelog2VOS;
        }));

        //更新缓存，返回结果
        String s = JSON.toJSONString(parent_cid);
        redis.opsForValue().set("catalogJSON", s, 1, TimeUnit.DAYS);
        return parent_cid;
    }

    /**
     * 本地锁
     *
     * @return
     */
//    @Override
    public Map<String, List<Catelog2VO>> getCatalogJsonFromDBWithLocalLock() {
        //加锁
        synchronized (this) {
            String catalogJSON = redis.opsForValue().get("catalogJSON");
            if (!StringUtils.isEmpty(catalogJSON)) {
                System.out.println("缓存命中");
                //命中直接返回
                Map<String, List<Catelog2VO>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catelog2VO>>>() {
                });
                return result;
            }

            System.out.println("没命中，查询了数据库");
            List<CategoryEntity> selectList = baseMapper.selectList(null);

            //查出所有一级分类
            List<CategoryEntity> level1Categorys = getParent_cid(selectList, 0L);
            Map<String, List<Catelog2VO>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
                //查二级分类
                List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
                List<Catelog2VO> catelog2VOS = null;
                if (categoryEntities != null) {
                    catelog2VOS = categoryEntities.stream().map(l2 -> {
                        Catelog2VO catelog2VO = new Catelog2VO(
                                v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName()
                        );
                        //找三级分类并封装
                        List<CategoryEntity> level3Catelog = getParent_cid(selectList, l2.getCatId());
                        if (level3Catelog != null) {
                            List<Catelog2VO.Catelog3VO> collect = level3Catelog.stream().map(l3 -> {
                                Catelog2VO.Catelog3VO catelog3VO = new Catelog2VO.Catelog3VO(
                                        l2.getCatId().toString(), l3.getCatId().toString(), l3.getName()
                                );
                                return catelog3VO;
                            }).collect(Collectors.toList());
                            catelog2VO.setCatalog3List(collect);
                        }

                        return catelog2VO;
                    }).collect(Collectors.toList());
                }
                return catelog2VOS;
            }));

            //更新缓存，返回结果
            String s = JSON.toJSONString(parent_cid);
            redis.opsForValue().set("catalogJSON", s, 1, TimeUnit.DAYS);
            return parent_cid;
        }
    }

    //抽取方法
    private List<CategoryEntity> getParent_cid(List<CategoryEntity> selectList, Long parent_cid) {
        List<CategoryEntity> collect = selectList.stream().filter(item -> item.getParentCid().equals(parent_cid))
                .collect(Collectors.toList());
        return collect;
    }

    private void findParentPath(Long catelogId, List<Long> path) {
        CategoryEntity category = this.getById(catelogId);
        if (category != null && !category.getParentCid().equals(0L)) {
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
            return (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort());
        }).collect(Collectors.toList());

        return children;
    }

}