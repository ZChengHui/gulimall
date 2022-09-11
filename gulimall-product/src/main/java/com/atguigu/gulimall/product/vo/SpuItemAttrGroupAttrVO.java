package com.atguigu.gulimall.product.vo;

import lombok.Data;
import lombok.ToString;

import java.util.List;

//分组名及其下的属性名属性值
@ToString
@Data
public class SpuItemAttrGroupAttrVO {
    private String groupName;
    private List<Attr> attrs;
}
