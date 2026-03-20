package com.ayanami.utils;


import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {


    //判断是否有该用户
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.判断是否需要拦截（ThreadHolder中是否有用户）
        if(UserHolder.getUser()==null){
            response.setStatus(401);
            return false;
        }
        //有用户，放行
        return true;
    }}


