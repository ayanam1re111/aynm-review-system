package com.ayanami.dto;

import com.ayanami.entity.Shop;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Elasticsearch 商铺索引文档。
 */
@Data
public class ShopDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商铺 id */
    private Long shopId;
    private String name;
    private Long typeId;
    private String typeName;
    private String address;
    private String area;
    /** 评分（1~5 分乘 10 保存） */
    private Integer score;
    private Integer sold;
    private Integer comments;
    private Double x;
    private Double y;
    /** geo_point 字段，值为 "纬度,经度" */
    @JsonIgnore
    private String location;
    private String images;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 商铺实体转索引文档。
     */
    public static ShopDocument fromShop(Shop shop, String typeName) {
        ShopDocument doc = new ShopDocument();
        doc.setShopId(shop.getId());
        doc.setName(shop.getName());
        doc.setTypeId(shop.getTypeId());
        doc.setTypeName(typeName);
        doc.setAddress(shop.getAddress());
        doc.setArea(shop.getArea());
        doc.setScore(shop.getScore());
        doc.setSold(shop.getSold());
        doc.setComments(shop.getComments());
        doc.setX(shop.getX());
        doc.setY(shop.getY());
        if (shop.getX() != null && shop.getY() != null) {
            // location 为 "纬度,经度"
            doc.setLocation(shop.getY() + "," + shop.getX());
        }
        doc.setImages(shop.getImages());
        doc.setCreateTime(shop.getCreateTime());
        doc.setUpdateTime(shop.getUpdateTime());
        return doc;
    }

    /**
     * 索引文档转商铺实体。
     */
    public Shop toShop() {
        Shop shop = new Shop();
        shop.setId(shopId);
        shop.setName(name);
        shop.setTypeId(typeId);
        shop.setAddress(address);
        shop.setArea(area);
        shop.setScore(score);
        shop.setSold(sold);
        shop.setComments(comments);
        shop.setX(x);
        shop.setY(y);
        shop.setImages(images);
        shop.setCreateTime(createTime);
        shop.setUpdateTime(updateTime);
        return shop;
    }
}
