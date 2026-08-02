package com.ayanami.dto;

import lombok.Data;

import java.util.List;

/**
 * 搜索分页返回结果。
 */
@Data
public class SearchPageDTO {

    private List<ShopSearchVO> list;

    private Long total;

    private Long current;

    private Long pages;

    public SearchPageDTO() {
    }

    public SearchPageDTO(List<ShopSearchVO> list, Long total, Long current, Long pages) {
        this.list = list;
        this.total = total;
        this.current = current;
        this.pages = pages;
    }
}
