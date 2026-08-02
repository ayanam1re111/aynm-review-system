package com.ayanami.controller;

import com.ayanami.dto.Result;
import com.ayanami.service.IRecommendService;
import com.ayanami.utils.SystemConstants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 个性化推荐接口，未登录返回热门。
 */
@RestController
@RequestMapping("/recommend")
public class RecommendController {

    @Resource
    private IRecommendService recommendService;

    /**
     * 商铺推荐，x/y 为当前位置。
     */
    @GetMapping("/shops")
    public Result recommendShops(
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "5") Integer pageSize) {
        if (current == null || current < 1) {
            current = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = SystemConstants.DEFAULT_PAGE_SIZE;
        }
        if (pageSize > SystemConstants.MAX_PAGE_SIZE) {
            pageSize = SystemConstants.MAX_PAGE_SIZE;
        }
        return recommendService.queryRecommendShops(x, y, current, pageSize);
    }

    /**
     * 博客推荐。
     */
    @GetMapping("/blogs")
    public Result recommendBlogs(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "5") Integer pageSize) {
        if (current == null || current < 1) {
            current = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = SystemConstants.DEFAULT_PAGE_SIZE;
        }
        if (pageSize > SystemConstants.MAX_PAGE_SIZE) {
            pageSize = SystemConstants.MAX_PAGE_SIZE;
        }
        return recommendService.queryRecommendBlogs(current, pageSize);
    }
}
