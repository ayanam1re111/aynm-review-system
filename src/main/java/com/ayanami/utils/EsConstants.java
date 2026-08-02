package com.ayanami.utils;

/**
 * Elasticsearch 字段名与排序方式常量。
 */
public class EsConstants {

    public static final String DEFAULT_SHOP_INDEX_NAME = "shop_index";

    public static final String FIELD_SHOP_ID = "shopId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_TYPE_ID = "typeId";
    public static final String FIELD_TYPE_NAME = "typeName";
    public static final String FIELD_ADDRESS = "address";
    public static final String FIELD_AREA = "area";
    public static final String FIELD_SCORE = "score";
    public static final String FIELD_SOLD = "sold";
    public static final String FIELD_COMMENTS = "comments";
    /** geo_point 字段，值为 "纬度,经度" */
    public static final String FIELD_LOCATION = "location";
    public static final String FIELD_IMAGES = "images";
    public static final String FIELD_CREATE_TIME = "createTime";
    public static final String FIELD_UPDATE_TIME = "updateTime";

    public static final String SORT_RELEVANCE = "relevance";
    public static final String SORT_SCORE = "score";
    public static final String SORT_DISTANCE = "distance";

    private EsConstants() {
    }
}
