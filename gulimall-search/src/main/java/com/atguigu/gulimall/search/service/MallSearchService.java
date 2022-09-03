package com.atguigu.gulimall.search.service;

import com.atguigu.gulimall.vo.SearchParamVO;
import com.atguigu.gulimall.vo.SearchResultVO;

public interface MallSearchService {
    SearchResultVO search(SearchParamVO param);
}
