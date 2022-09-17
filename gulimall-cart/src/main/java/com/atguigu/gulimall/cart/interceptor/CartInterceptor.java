package com.atguigu.gulimall.cart.interceptor;

import com.atguigu.common.constant.AuthServerConstant;
import com.atguigu.common.constant.CartConstant;
import com.atguigu.common.vo.MemberResponseVO;
import com.atguigu.gulimall.cart.to.UserInfoTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.UUID;

//拦截器组件
public class CartInterceptor implements HandlerInterceptor {

    public static ThreadLocal<UserInfoTO> threadLocal = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        HttpSession session = request.getSession();
        MemberResponseVO vo = (MemberResponseVO) session.getAttribute(AuthServerConstant.LOGIN_USER);
        UserInfoTO to = new UserInfoTO();
        if (vo != null) {
            //用户已登录 设置userid
            to.setUserId(vo.getId());
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null && cookies.length > 0) {
            for (Cookie cookie : cookies) {
                String name = cookie.getName();
                if (name.equals(CartConstant.TEMP_USER_COOKIE_NAME)) {
                    //user-key cookie存在，则设置值
                    to.setUserKey(cookie.getValue());
                    //临时用户
                    to.setTempUser(true);
                }
            }
        }
        //线程本地存储
        if (StringUtils.isEmpty(to.getUserKey())) {
            //生成key
            to.setUserKey(UUID.randomUUID().toString());
        }
        threadLocal.set(to);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        UserInfoTO userInfoTO = threadLocal.get();
        //是临时用户
        if (!userInfoTO.isTempUser()) {
            Cookie cookie = new Cookie(
                    CartConstant.TEMP_USER_COOKIE_NAME,
                    userInfoTO.getUserKey()
            );
            cookie.setDomain("gulimall.com");
            cookie.setMaxAge(CartConstant.TEMP_USER_COOKIE_TIMEOUT);
            response.addCookie(cookie);
        }
    }
}
