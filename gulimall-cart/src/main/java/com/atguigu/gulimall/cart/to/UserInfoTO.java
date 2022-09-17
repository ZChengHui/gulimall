package com.atguigu.gulimall.cart.to;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class UserInfoTO {

    private Long userId;
    private String userKey;
    private boolean tempUser = false;

}
