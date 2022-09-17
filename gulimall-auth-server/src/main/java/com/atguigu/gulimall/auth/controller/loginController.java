package com.atguigu.gulimall.auth.controller;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.AuthServerConstant;
import com.atguigu.common.exception.BizCodeEnume;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberResponseVO;
import com.atguigu.gulimall.auth.feign.MemberFeignService;
import com.atguigu.gulimall.auth.feign.ThirdPartFeignService;
import com.atguigu.gulimall.auth.vo.UserLoginVO;
import com.atguigu.gulimall.auth.vo.UserRegisterVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.atguigu.common.constant.AuthServerConstant.LOGIN_USER;

@Controller
public class loginController {

    @Autowired
    private MemberFeignService memberFeignService;

    @Autowired
    private ThirdPartFeignService thirdPartFeignService;

    @Autowired
    private StringRedisTemplate redis;

    @PostMapping("/login")
    public String login(@Validated UserLoginVO vo, BindingResult result, RedirectAttributes redirectAttributes, HttpSession session) {
        //数据校验
        if (result.hasErrors()) {

            Map<String, String> errors = result.getFieldErrors().stream().collect(Collectors.toMap(
                    FieldError::getField,
                    FieldError::getDefaultMessage,
                    (a,b) -> a
            ));
            //重定向存取数据方式
            redirectAttributes.addFlashAttribute("errors", errors);
            //校验出错，转发到注册页
            return "redirect:http://auth.gulimall.com/login.html";
        }
        //远程调用登录
        R r = memberFeignService.login(vo);
        if (r.getCode() == 0) {
            //登录成功
            MemberResponseVO data = r.getData("data",new TypeReference<MemberResponseVO>(){});
            //session不能跨域名共享
            //TODO 扩大域名至父域名
            //使用JSON序列化方式
            session.setAttribute(LOGIN_USER, data);
            return "redirect:http://gulimall.com";
        } else {
            //存放错误消息
            Map<String, String> errors = new HashMap<>();
            errors.put("msg", r.getMsg());
            redirectAttributes.addFlashAttribute("errors", errors);
            return "redirect:http://auth.gulimall.com/login.html";
        }

    }

    /**
     * TODO 重定向携带数据，利用session，去除数据后清除
     * TODO 分布式下session问题
     * RedirectAttributes 模拟重定向携带数据
     * @param vo
     * @param result
     * @param redirectAttributes
     * @param session
     * @return
     */
    @PostMapping("/register")
    public String register(@Validated UserRegisterVO vo, BindingResult result, RedirectAttributes redirectAttributes, HttpSession session) {

        if (result.hasErrors()) {

            Map<String, String> errors = result.getFieldErrors().stream().collect(Collectors.toMap(
                    FieldError::getField,
                    FieldError::getDefaultMessage,
                    (a,b) -> a
            ));
            //重定向存取数据方式
            redirectAttributes.addFlashAttribute("errors", errors);
            //校验出错，转发到注册页
            return "redirect:http://auth.gulimall.com/reg.html";
        }
        //注册逻辑
        //验证码比对
        String code = vo.getCode();
        String s = redis.opsForValue().get(AuthServerConstant.SMS_CODE_CACHE_PREFIX + vo.getPhone());
        if (!StringUtils.isEmpty(s)) {
            s = s.split("_")[0];
            if (code.equals(s)) {
                //验证码正确 删除验证码
                redis.delete(AuthServerConstant.SMS_CODE_CACHE_PREFIX+vo.getPhone());
                //远程调用注册
                R r = memberFeignService.register(vo);
                if (r.getCode() == 0) {

                    return "redirect:http://auth.gulimall.com/login.html";
                } else {
                    Map<String, String> errors = new HashMap<>();
                    //取出R中的异常信息 放入映射
                    errors.put("msg", r.getMsg());
                    //存入redirectAttributes
                    redirectAttributes.addFlashAttribute("errors", errors);
                    return "redirect:http://auth.gulimall.com/reg.html";
                }

            } else {
                Map<String, String> errors = new HashMap<>();
                errors.put("code", "验证码错误");
                redirectAttributes.addFlashAttribute("errors", errors);
                return "redirect:http://auth.gulimall.com/reg.html";
            }
        } else {
            Map<String, String> errors = new HashMap<>();
            errors.put("code", "验证码错误");
            redirectAttributes.addFlashAttribute("errors", errors);
            return "redirect:http://auth.gulimall.com/reg.html";
        }

    }

    //短信验证码
    @ResponseBody
    @GetMapping("/sms/sendCode")
    public R sendCode(@RequestParam("phone") String phone) {
        //TODO 接口防刷

        //限制验证码获取频率
        String s = redis.opsForValue().get(AuthServerConstant.SMS_CODE_CACHE_PREFIX + phone);
        if (!StringUtils.isEmpty(s)) {
            Long t = Long.parseLong(s.split("_")[1]);
            if (System.currentTimeMillis() - t < 60000) {
                //60秒内不能再发
                return R.error(BizCodeEnume.SMS_CODE_EXCEPTION.getCode(), BizCodeEnume.SMS_CODE_EXCEPTION.getMsg());
            }
        }

        String code = UUID.randomUUID().toString().substring(0, 4);
        String substring = code + "_" + System.currentTimeMillis();

        //缓存验证码
        redis.opsForValue().set(AuthServerConstant.SMS_CODE_CACHE_PREFIX+phone, substring, 1, TimeUnit.MINUTES);

        thirdPartFeignService.sendCode(phone, code);
        return R.ok();
    }

}
