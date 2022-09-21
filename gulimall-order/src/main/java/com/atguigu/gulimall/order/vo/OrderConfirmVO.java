package com.atguigu.gulimall.order.vo;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

//订单确认页数据
@ToString
public class OrderConfirmVO implements Serializable {

    //收货地址列表
    @Setter @Getter
    private List<MemberAddressVO> address;

    //所有选中的购物项
    @Setter @Getter
    private List<OrderItemVO> items;

    //库存信息
    @Setter @Getter
    Map<Long, Boolean> stocks;

    //优惠劵信息
    @Setter @Getter
    private Integer integration;//积分

    //防重令牌
    @Getter @Setter
    private String orderToken;

    //总件数
    public Integer getCount() {
        Integer count = 0;
        if (items != null && items.size() > 0) {
            for (OrderItemVO vo : items) {
                count += vo.getCount();
            }
        }
        return count;
    }

    //订单总额
    public BigDecimal getTotal() {
        BigDecimal total = new BigDecimal(0);
        if (items != null && items.size() > 0) {
            for (OrderItemVO itemVO : items) {
                BigDecimal multiply = itemVO.getPrice().multiply(new BigDecimal(itemVO.getCount()));
                total = total.add(multiply);
            }
        }
        return total;
    }

    //应付总额
    public BigDecimal getPayPrice() {
        return getTotal();
    }

}
