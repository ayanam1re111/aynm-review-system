package com.ayanami.service;

import com.ayanami.dto.Result;

/**
 * 个性化推荐服务：商铺与博客推荐，未登录返回热门，不足用热门补足。
 */
public interface IRecommendService {

    /**
     * 商铺推荐，x/y 为当前位置（用于距离因子）。
     */
    Result queryRecommendShops(Double x, Double y, Integer current, Integer pageSize);

    /**
     * 博客推荐。
     */
    Result queryRecommendBlogs(Integer current, Integer pageSize);
}
