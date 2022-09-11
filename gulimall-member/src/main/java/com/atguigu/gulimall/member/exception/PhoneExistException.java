package com.atguigu.gulimall.member.exception;

public class PhoneExistException extends RuntimeException{
    public PhoneExistException() {
        super("电话号已注册");
    }
}
