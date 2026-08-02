package com.ayanami.service;

import com.ayanami.dto.SearchPageDTO;
import com.ayanami.entity.Shop;

import java.io.IOException;

/**
 * 商铺全文检索服务：索引重建、增量同步与搜索。
 */
public interface ISearchService {

    /**
     * 全量重建索引：索引不存在则创建并全量导入商铺数据（仅供开发环境调用）。
     */
    void rebuildIndex() throws IOException;

    /**
     * 新增或更新商铺索引文档，ES 异常不影响主业务。
     */
    void saveDocument(Shop shop);

    /**
     * 按商铺 id 删除索引文档。
     */
    void deleteDocument(Long shopId);

    /**
     * 商铺搜索：关键词 / 类型 / 最低评分筛选，高亮，相关度/评分/距离排序与分页。
     * ES 不可用或未启用时降级为数据库查询。
     */
    SearchPageDTO search(String keyword, Long typeId, Integer minScore,
                         Double x, Double y, String sortType,
                         Integer current, Integer pageSize);
}
