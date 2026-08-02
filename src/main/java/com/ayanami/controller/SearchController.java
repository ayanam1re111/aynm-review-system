package com.ayanami.controller;

import cn.hutool.core.util.StrUtil;
import com.ayanami.dto.Result;
import com.ayanami.dto.SearchPageDTO;
import com.ayanami.dto.UserDTO;
import com.ayanami.service.ISearchService;
import com.ayanami.service.UserBehaviorService;
import com.ayanami.utils.SystemConstants;
import com.ayanami.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 商铺全文检索接口。
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    @Resource
    private ISearchService searchService;
    @Resource
    private UserBehaviorService userBehaviorService;

    /**
     * 商铺全文搜索。
     * sortType 支持 relevance（相关度）/ score（评分）/ distance（距离，需传 x/y）。
     */
    @GetMapping("/shops")
    public Result searchShops(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "typeId", required = false) Long typeId,
            @RequestParam(value = "minScore", required = false) Integer minScore,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y,
            @RequestParam(value = "sortType", defaultValue = "relevance") String sortType,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "5") Integer pageSize) {
        // 分页参数兜底，防止一次查询过多
        if (current == null || current < 1) {
            current = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = SystemConstants.DEFAULT_PAGE_SIZE;
        }
        if (pageSize > SystemConstants.MAX_PAGE_SIZE) {
            pageSize = SystemConstants.MAX_PAGE_SIZE;
        }
        SearchPageDTO page = searchService.search(
                StrUtil.trimToNull(keyword), typeId, minScore, x, y, sortType, current, pageSize);
        // 记录搜索行为（未登录不记录）
        if (typeId != null) {
            UserDTO user = UserHolder.getUser();
            if (user != null) {
                userBehaviorService.recordSearch(user.getId(), typeId);
            }
        }
        return Result.ok(page.getList(), page.getTotal());
    }
}
