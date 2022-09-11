package com.atguigu.gulimall.auth.vo;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;

@Data
public class UserLoginVO {

    @NotBlank(message = "账号不能为空")
    @Length(min = 6, max = 18, message = "用户名6-18位字符")
    private String loginname;

    @NotBlank(message = "密码不能为空")
    @Length(min = 6, max = 18, message = "密码6-18位字符")
    private String password;

}
