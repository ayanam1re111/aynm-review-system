package com.ayanami.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商铺搜索结果视图：在索引文档基础上增加高亮字段与距离。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopSearchVO extends ShopDocument {

    private static final long serialVersionUID = 1L;

    /** 高亮后的商铺名称 */
    private String highlightName;

    /** 高亮后的地址 */
    private String highlightAddress;

    /** 与搜索坐标的距离（km） */
    private Double distance;
}
