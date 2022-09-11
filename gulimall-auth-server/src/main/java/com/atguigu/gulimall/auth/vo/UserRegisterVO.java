package com.atguigu.gulimall.auth.vo;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 注册表单信息
 * 数据校验
 */
@Data
public class UserRegisterVO {

    @NotBlank(message = "用户名必须提交")
    @Length(min = 6, max = 18, message = "用户名6-18位字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Length(min = 6, max = 18, message = "密码6-18位字符")
    private String password;

    @NotBlank(message = "手机号必须填写")
    @Pattern(regexp = "^[1]([3-9])[0-9]{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码必须填写")
    @Length(min = 4, max = 4, message = "验证码长度不正确")
    private String code;

}
