package com.atguigu.gulimall.ware.vo;

import lombok.Data;

import java.util.List;

@Data
public class MergeVO {
    private List<Long> items;
    private Long purchaseId;
}
