package com.ayanami.controller;

import com.ayanami.dto.Result;
import com.ayanami.service.ISearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 搜索索引管理接口（仅供开发环境使用），请求头 admin-key 做简单保护。
 */
@Slf4j
@RestController
@RequestMapping("/admin/search")
public class AdminSearchController {

    @Resource
    private ISearchService searchService;

    @Value("${hm.search.admin-key:dev-admin-key}")
    private String adminKey;

    /**
     * 全量重建商铺索引：自动创建索引并导入 MySQL 商铺数据。
     */
    @PostMapping("/shop/rebuild")
    public Result rebuildShopIndex(@RequestHeader(value = "admin-key", required = false) String key) {
        if (key == null || !adminKey.equals(key)) {
            return Result.fail("无权限，拒绝操作");
        }
        try {
            searchService.rebuildIndex();
            return Result.ok("商铺索引重建完成");
        } catch (Exception e) {
            log.error("商铺索引重建失败", e);
            return Result.fail("商铺索引重建失败：" + e.getMessage());
        }
    }
}
