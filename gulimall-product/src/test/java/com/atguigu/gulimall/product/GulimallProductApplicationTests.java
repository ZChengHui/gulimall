package com.atguigu.gulimall.product;

import com.atguigu.gulimall.product.dao.AttrGroupDao;
import com.atguigu.gulimall.product.dao.SkuSaleAttrValueDao;
import com.atguigu.gulimall.product.entity.BrandEntity;
import com.atguigu.gulimall.product.service.BrandService;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.SkuItemSaleAttrVO;
import com.atguigu.gulimall.product.vo.SpuItemAttrGroupAttrVO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@RunWith(SpringRunner.class)
public class GulimallProductApplicationTests {

    @Autowired
    BrandService brandService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedissonClient redisson;

    @Autowired
    AttrGroupDao attrGroupDao;

    @Autowired
    SkuSaleAttrValueDao skuSaleAttrValueDao;

    @Test
    public void getSaleAttrsBySpuId() {
        List<SkuItemSaleAttrVO> attrs = skuSaleAttrValueDao.getSaleAttrsBySpuId(18L);
        System.out.println(attrs);
    }

    @Test
    public void getAttrGroupWithAttrsBySpuId() {
        List<SpuItemAttrGroupAttrVO> group = attrGroupDao.getAttrGroupWithAttrsBySpuId(18L, 225L);
        System.out.println(group);
    }

    @Test
    public void testRedisson() {
        RLock lock = redisson.getLock("my-lock");
        //10秒自动解锁
        lock.lock(10, TimeUnit.SECONDS);
        try {
            System.out.println("加锁成功："+Thread.currentThread().getId());
            Thread.sleep(30000);
        }catch (Exception e) {

        }finally {
            System.out.println("解锁："+Thread.currentThread().getId());
        }
    }

    @Test
    public void testRedis() {
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        ops.set("name", "zch");
        System.out.println(ops.get("name"));
    }

    @Test
    public void contextLoads() {
        BrandEntity brandEntity = new BrandEntity();
        brandEntity = brandService.getById(1L);
        System.out.println(brandEntity);
    }

    @Test
    public void findParent() {
        Long[] arr = categoryService.findCatelogPath(225L);
        System.out.println(Arrays.asList(arr));
    }
}
