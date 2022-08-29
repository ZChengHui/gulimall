package com.atguigu.gulimall.product.web;

import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.Catelog2VO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
public class IndexController {

    @Autowired
    CategoryService categoryService;

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

}
