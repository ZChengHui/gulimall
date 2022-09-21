package com.atguigu.common.exception;

public class NoStockException extends RuntimeException{
    private Long skuId;
    public NoStockException() {
        super("没有足够库存");
    }
    public NoStockException(Long skuId) {
        super("商品id:"+skuId+"没有足够库存");
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }
}
