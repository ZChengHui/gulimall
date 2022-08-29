package com.atguigu.gulimall.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Catelog2VO {
    private String catalog1Id; // 父分类id
    private List<Catelog3VO> catalog3List; // 三级子分类
    private String id;
    private String name;

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Catelog3VO{
        private String catalog2Id;
        private String id;
        private String name;
    }
}
