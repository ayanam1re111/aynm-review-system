package com.ayanami.service.impl;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.ayanami.dto.SearchPageDTO;
import com.ayanami.dto.ShopDocument;
import com.ayanami.dto.ShopSearchVO;
import com.ayanami.entity.Shop;
import com.ayanami.entity.ShopType;
import com.ayanami.service.ISearchService;
import com.ayanami.service.IShopService;
import com.ayanami.service.IShopTypeService;
import com.ayanami.utils.EsConstants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商铺全文检索：索引重建、增量同步与搜索，ES 异常时降级为数据库查询。
 */
@Slf4j
@Service
public class ShopSearchServiceImpl implements ISearchService {

    @Resource
    private IShopService shopService;
    @Resource
    private IShopTypeService shopTypeService;
    @Resource
    private ObjectMapper objectMapper;

    /** ES 客户端，未启用时为空 */
    @Autowired(required = false)
    private RestHighLevelClient client;

    @Value("${hm.es.enabled:true}")
    private boolean esEnabled;

    @Value("${hm.es.index-name:shop_index}")
    private String indexName;

    private static final String MAPPING_PATH = "es/shop_index_mapping.json";

    @Override
    public void rebuildIndex() throws IOException {
        if (!esEnabled || client == null) {
            throw new IllegalStateException("Elasticsearch 未启用或未配置，无法重建索引");
        }
        // 索引不存在则创建（含 mapping）
        org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest existsRequest =
                new org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest(indexName);
        boolean exists = client.indices().exists(existsRequest, RequestOptions.DEFAULT);
        if (!exists) {
            String mappingJson = loadMappingJson();
            org.elasticsearch.action.admin.indices.create.CreateIndexRequest createRequest =
                    new org.elasticsearch.action.admin.indices.create.CreateIndexRequest(indexName);
            createRequest.source(mappingJson, XContentType.JSON);
            client.indices().create(createRequest, RequestOptions.DEFAULT);
            log.info("商铺索引已创建, index={}", indexName);
        } else {
            log.info("商铺索引已存在, 跳过创建, index={}", indexName);
        }

        // 预加载商铺类型，避免全量导入时逐条查库
        Map<Long, String> typeNames = loadTypeNames();

        // 分页全量导入
        int pageSize = 200;
        long total = shopService.count();
        long pages = (total + pageSize - 1) / pageSize;
        for (long p = 1; p <= pages; p++) {
            Page<Shop> page = shopService.page(new Page<>(p, pageSize));
            List<Shop> shops = page.getRecords();
            if (shops.isEmpty()) {
                break;
            }
            BulkRequest bulk = new BulkRequest();
            for (Shop shop : shops) {
                ShopDocument doc = ShopDocument.fromShop(shop, typeNames.get(shop.getTypeId()));
                bulk.add(new IndexRequest(indexName)
                        .id(doc.getShopId().toString())
                        .source(toSourceMap(doc), XContentType.JSON));
            }
            BulkResponse bulkResponse = client.bulk(bulk, RequestOptions.DEFAULT);
            if (bulkResponse.hasFailures()) {
                log.error("商铺全量导入存在失败条目: {}", bulkResponse.buildFailureMessage());
            }
        }
        log.info("商铺索引全量重建完成, total={}, index={}", total, indexName);
    }

    @Override
    public void saveDocument(Shop shop) {
        if (shop == null || shop.getId() == null || !esEnabled || client == null) {
            return;
        }
        try {
            ShopType shopType = shop.getTypeId() == null ? null : shopTypeService.getById(shop.getTypeId());
            String typeName = shopType == null ? null : shopType.getName();
            ShopDocument doc = ShopDocument.fromShop(shop, typeName);
            IndexRequest request = new IndexRequest(indexName)
                    .id(doc.getShopId().toString())
                    .source(toSourceMap(doc), XContentType.JSON);
            DocWriteResponse response = client.index(request, RequestOptions.DEFAULT);
            log.debug("商铺索引写入成功, shopId={}, result={}", shop.getId(), response.getResult());
        } catch (Exception e) {
            // ES 异常不影响 MySQL 主业务，只记录日志
            log.error("商铺索引写入失败, shopId={}, 主业务不受影响", shop.getId(), e);
        }
    }

    @Override
    public void deleteDocument(Long shopId) {
        if (shopId == null || !esEnabled || client == null) {
            return;
        }
        try {
            DeleteRequest request = new DeleteRequest(indexName, String.valueOf(shopId));
            client.delete(request, RequestOptions.DEFAULT);
            log.debug("商铺索引删除成功, shopId={}", shopId);
        } catch (Exception e) {
            log.error("商铺索引删除失败, shopId={}, 主业务不受影响", shopId, e);
        }
    }

    @Override
    public SearchPageDTO search(String keyword, Long typeId, Integer minScore,
                                Double x, Double y, String sortType,
                                Integer current, Integer pageSize) {
        // ES 未启用或异常时降级为数据库查询
        if (esEnabled && client != null) {
            try {
                return searchByEs(keyword, typeId, minScore, x, y, sortType, current, pageSize);
            } catch (Exception e) {
                log.warn("ES 搜索异常，降级为数据库查询, keyword={}, reason={}", keyword, e.getMessage());
            }
        }
        return searchByDb(keyword, typeId, minScore, x, y, sortType, current, pageSize);
    }

    // ==================== ES 搜索 ====================

    private SearchPageDTO searchByEs(String keyword, Long typeId, Integer minScore,
                                     Double x, Double y, String sortType,
                                     Integer current, Integer pageSize) throws IOException {
        // 构建查询：关键词匹配名称/地址，类型与最低评分用 filter
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        if (StrUtil.isNotBlank(keyword)) {
            boolQuery.must(QueryBuilders.multiMatchQuery(keyword, EsConstants.FIELD_NAME, EsConstants.FIELD_ADDRESS));
        } else {
            boolQuery.must(QueryBuilders.matchAllQuery());
        }
        if (typeId != null) {
            boolQuery.filter(QueryBuilders.termQuery(EsConstants.FIELD_TYPE_ID, typeId));
        }
        if (minScore != null) {
            RangeQueryBuilder range = QueryBuilders.rangeQuery(EsConstants.FIELD_SCORE);
            boolQuery.filter(range.gte(minScore));
        }

        // 分页与排序
        SearchSourceBuilder source = new SearchSourceBuilder();
        source.query(boolQuery);
        source.from(Math.max(0, (current - 1) * pageSize)).size(pageSize);
        boolean sortByDistance = EsConstants.SORT_DISTANCE.equals(sortType) && x != null && y != null;
        if (EsConstants.SORT_SCORE.equals(sortType)) {
            source.sort(SortBuilders.fieldSort(EsConstants.FIELD_SCORE).order(SortOrder.DESC));
        } else if (sortByDistance) {
            // geo_distance 排序，GeoPoint(lat, lon) = (y, x)
            source.sort(SortBuilders.geoDistanceSort(EsConstants.FIELD_LOCATION, new GeoPoint(y, x))
                    .unit(DistanceUnit.KILOMETERS).order(SortOrder.ASC));
        } else {
            source.sort(SortBuilders.scoreSort());
        }

        // 名称与地址高亮
        HighlightBuilder highlight = new HighlightBuilder();
        highlight.field(EsConstants.FIELD_NAME).field(EsConstants.FIELD_ADDRESS)
                .preTags("<em>").postTags("</em>");
        source.highlighter(highlight);

        // 执行查询
        SearchRequest request = new SearchRequest(indexName);
        request.source(source);
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        // 解析结果
        SearchHits hits = response.getHits();
        List<ShopSearchVO> list = new ArrayList<>(hits.getHits().length);
        for (SearchHit hit : hits.getHits()) {
            Map<String, Object> map = hit.getSourceAsMap();
            ShopSearchVO vo = objectMapper.convertValue(map, ShopSearchVO.class);
            Map<String, HighlightField> hf = hit.getHighlightFields();
            if (hf.containsKey(EsConstants.FIELD_NAME)) {
                vo.setHighlightName(hf.get(EsConstants.FIELD_NAME).fragments()[0].string());
            }
            if (hf.containsKey(EsConstants.FIELD_ADDRESS)) {
                vo.setHighlightAddress(hf.get(EsConstants.FIELD_ADDRESS).fragments()[0].string());
            }
            // 按距离排序时 sort value 第一个元素为距离
            if (sortByDistance && hit.getSortValues().length > 0) {
                Object d = hit.getSortValues()[0];
                if (d instanceof Number) {
                    vo.setDistance(((Number) d).doubleValue());
                }
            }
            list.add(vo);
        }
        long total = hits.getTotalHits().value;
        long pages = (total + pageSize - 1) / pageSize;
        return new SearchPageDTO(list, total, (long) current, pages);
    }

    // ==================== 数据库降级查询 ====================

    private SearchPageDTO searchByDb(String keyword, Long typeId, Integer minScore,
                                     Double x, Double y, String sortType,
                                     Integer current, Integer pageSize) {
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(keyword), "name", keyword)
                .eq(typeId != null, "type_id", typeId)
                .ge(minScore != null, "score", minScore)
                .orderByDesc(EsConstants.SORT_SCORE.equals(sortType) ? "score" : "sold")
                .page(new Page<>(current, pageSize));

        List<ShopSearchVO> list = page.getRecords().stream().map(shop -> {
            String typeName = null;
            ShopType shopType = shopTypeService.getById(shop.getTypeId());
            if (shopType != null) {
                typeName = shopType.getName();
            }
            ShopDocument doc = ShopDocument.fromShop(shop, typeName);
            ShopSearchVO vo = objectMapper.convertValue(doc, ShopSearchVO.class);
            // 降级场景下对名称/地址做简单高亮
            if (StrUtil.isNotBlank(keyword)) {
                String hl = "<em>" + keyword + "</em>";
                if (StrUtil.isNotBlank(vo.getName()) && vo.getName().contains(keyword)) {
                    vo.setHighlightName(vo.getName().replace(keyword, hl));
                }
                if (StrUtil.isNotBlank(vo.getAddress()) && vo.getAddress().contains(keyword)) {
                    vo.setHighlightAddress(vo.getAddress().replace(keyword, hl));
                }
            }
            return vo;
        }).collect(Collectors.toList());
        return new SearchPageDTO(list, page.getTotal(), page.getCurrent(), page.getPages());
    }

    // ==================== 工具方法 ====================

    /**
     * 文档转 ES 写入的 map，location 字段为 @JsonIgnore 需手动补齐。
     */
    private Map<String, Object> toSourceMap(ShopDocument doc) {
        Map<String, Object> map = objectMapper.convertValue(doc, new TypeReference<Map<String, Object>>() {
        });
        if (doc.getLocation() != null) {
            map.put(EsConstants.FIELD_LOCATION, doc.getLocation());
        }
        return map;
    }

    /**
     * 加载全部商铺类型，返回 typeId → typeName。
     */
    private Map<Long, String> loadTypeNames() {
        List<ShopType> types = shopTypeService.list();
        Map<Long, String> map = new HashMap<>(types.size());
        for (ShopType type : types) {
            map.put(type.getId(), type.getName());
        }
        return map;
    }

    /**
     * 读取索引 mapping 资源文件。
     */
    private String loadMappingJson() throws IOException {
        try (InputStream in = new ClassPathResource(MAPPING_PATH).getInputStream()) {
            return IoUtil.readUtf8(in);
        }
    }
}
