package com.atguigu.gulimall.search.controller;

import com.atguigu.gulimall.search.service.MallSearchService;
import com.atguigu.gulimall.vo.SearchParamVO;
import com.atguigu.gulimall.vo.SearchResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
public class SearchController {

    @Autowired
    MallSearchService mallSearchService;

    @GetMapping({"/", "/list.html"})
    public String listPage(SearchParamVO param, Model model, HttpServletRequest request) {
        String queryString = request.getQueryString();
        param.set_queryString(queryString);
        //去ES中检索
        SearchResultVO result = mallSearchService.search(param);
        //返回给页面
        model.addAttribute("result", result);
        return "list";
    }
}
