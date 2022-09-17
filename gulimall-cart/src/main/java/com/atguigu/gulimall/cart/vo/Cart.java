package com.atguigu.gulimall.cart.vo;

import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车
 */
public class Cart {
    List<CartItem> items;

    private Integer countNum;//商品数量
    private Integer countType;//商品类型的数量
    private BigDecimal totalAmount;//商品总价
    private BigDecimal reduce = new BigDecimal(0);//减免价格

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
    }

    public Integer getCountNum() {
        Integer count = 0;
        if (!CollectionUtils.isEmpty(items)) {
            for (CartItem item : items) {
                count += item.getCount();
            }
        }
        return count;
    }

    public Integer getCountType() {
        Integer count = 0;
        if (!CollectionUtils.isEmpty(items)) {
            for (CartItem item : items) {
                count++;
            }
        }
        return count;
    }

    public BigDecimal getTotalAmount() {
        BigDecimal amount = new BigDecimal(0);
        if (!CollectionUtils.isEmpty(items)) {
            for (CartItem item : items) {
                amount = amount.add(item.getTotalPrice());
            }
            //减去优惠
            amount =  amount.subtract(getReduce());
        }
        return amount;
    }

    public BigDecimal getReduce() {
        return reduce;
    }

}
