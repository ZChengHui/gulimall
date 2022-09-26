package com.atguigu.gulimall.seckill.interceptor;

import com.atguigu.common.constant.AuthServerConstant;
import com.atguigu.common.vo.MemberResponseVO;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Component
public class LoginUserInterceptor implements HandlerInterceptor {

    public static ThreadLocal<MemberResponseVO> loginUser = new ThreadLocal<>();

    //配置拦截器
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        ///请求路径白名单 远程调用 头丢失
        /**
         * @GetMapping("/currentSeckillSkus")
         * @GetMapping("/sku/seckill/{skuId}")
         */
        String uri = request.getRequestURI();
        AntPathMatcher matcher = new AntPathMatcher();
        boolean matchKill = matcher.match("/kill", uri);

        //秒杀单独拦截
        if (matchKill) {
            HttpSession session = request.getSession();
            MemberResponseVO attribute = (MemberResponseVO) session.getAttribute(AuthServerConstant.LOGIN_USER);
            if (attribute != null) {
                //已登录 存入本地线程存储
                loginUser.set(attribute);
                return true;
            } else {
                //未登录
                session.setAttribute("msg", "请先登录");
                response.sendRedirect("http://auth.gulimall.com/login.html");
                return false;
            }
        } else {
            return true;
        }
    }
}

