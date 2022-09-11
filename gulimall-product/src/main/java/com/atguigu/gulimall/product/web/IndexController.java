package com.atguigu.gulimall.product.web;

import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.Catelog2VO;
import org.redisson.Redisson;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Controller
public class IndexController {

    @Autowired
    CategoryService categoryService;

    @Autowired
    RedissonClient redisson;

    @Autowired
    StringRedisTemplate redis;

    @GetMapping({"/", "index.html"})
    public String indexPage(Model model) {

        //TODO 1、查出所有一级分类
        List<CategoryEntity> categoryEntities = categoryService.getLevel1Categorys();

        //视图解析器拼串前后缀
        //  classpath:/templates  + + .html
        model.addAttribute("categorys", categoryEntities);
        return "index";
    }

    @ResponseBody
    @GetMapping("/index/json/catalog.json")
    public Map<String, List<Catelog2VO>> getCatalogJson() {
        Map<String, List<Catelog2VO>> map = categoryService.getCatalogJson();
        return map;
    }


//Redisson测试
//    @ResponseBody
//    @GetMapping("/hello")
//    public String hello() {
//        RLock lock = redisson.getLock("my-lock");
//        lock.lock(30, TimeUnit.SECONDS);//自动解锁时间大于业务时间
//        try {
//            System.out.println("加锁成功："+Thread.currentThread().getId());
//            Thread.sleep(10000);
//        }catch (Exception e) {
//
//        }finally {
//            System.out.println("解锁："+Thread.currentThread().getId());
//            lock.unlock();
//            return "hello";
//        }
//    }
//
//    //读写锁
//    @ResponseBody
//    @GetMapping("/write")
//    public String write() throws InterruptedException {
//        RReadWriteLock rw = redisson.getReadWriteLock("rw");
//        RLock wLock = rw.writeLock();
//        wLock.lock();
//        String s = UUID.randomUUID().toString();
//        redis.opsForValue().set("val", s);
//        Thread.sleep(10000);
//        wLock.unlock();
//        return s;
//    }
//
//    @ResponseBody
//    @GetMapping("/read")
//    public String read() throws InterruptedException {
//        RReadWriteLock rw = redisson.getReadWriteLock("rw");
//        RLock rLock = rw.readLock();
//        rLock.lock();
//        String s = redis.opsForValue().get("val").toString();
//        rLock.unlock();
//        return s;
//    }
//
//    //信号量
//    @ResponseBody
//    @GetMapping("/park")
//    public String park() throws InterruptedException {
//        RSemaphore loc = redisson.getSemaphore("loc");
//        loc.acquire();
//        return "ok";
//    }
//
//    @ResponseBody
//    @GetMapping("/go")
//    public String go() {
//        RSemaphore loc = redisson.getSemaphore("loc");
//        loc.release();
//        return "go";
//    }
//
//    //闭锁
//    @ResponseBody
//    @GetMapping("/lockdoor")
//    public String lockdoor() throws InterruptedException {
//        RCountDownLatch door = redisson.getCountDownLatch("door");
//        door.trySetCount(3);
//        door.await();
//        return "放假了锁门走人";
//    }
//
//    @ResponseBody
//    @GetMapping("/gogogo/{id}")
//    public String gogogo(@PathVariable("id") Long id) {
//        RCountDownLatch door = redisson.getCountDownLatch("door");
//        door.countDown();
//        return id+"班走了";
//    }

}
