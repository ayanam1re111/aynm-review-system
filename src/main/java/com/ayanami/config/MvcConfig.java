package com.ayanami.config;

import com.ayanami.utils.LoginInterceptor;
import com.ayanami.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    private final StringRedisTemplate stringRedisTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate2;

    public MvcConfig(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登录拦截器（第二步执行，拦截未登录）
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(//排除某些路径
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login",
                        "/test/token-batch",
                        // 推荐/搜索支持未登录访问；重建索引由 admin-key 保护
                        "/recommend/**",
                        "/search/**",
                        "/admin/search/**"
        ).order(1);
        //token刷新拦截器（第一步执行，只负责刷新token时间，无论有无token都放行
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);//拦截所有请求

    }
}
