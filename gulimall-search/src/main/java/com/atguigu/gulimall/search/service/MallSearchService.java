package com.atguigu.gulimall.search.service;

import com.atguigu.gulimall.search.vo.SearchParamVO;
import com.atguigu.gulimall.search.vo.SearchResultVO;

public interface MallSearchService {
    SearchResultVO search(SearchParamVO param);
}
