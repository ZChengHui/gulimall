package com.atguigu.gulimall.product.vo;

import lombok.Data;

@Data
public class AttrResponseVO extends AttrVo{
    //所属分类名字
    private String catelogName;

    //所属分组名字
    private String groupName;

    //具体分类路径
    private Long[] catelogPath;
}
