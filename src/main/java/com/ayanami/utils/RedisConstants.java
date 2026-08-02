package com.ayanami.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String CACHE_SHOPTYPE_KEY = "cache:shop_type:shop_type_list";
    public static final Long CACHE_SHOPTYPE_TTL = 60L;

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;


    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";

    /** 订单处理状态 key：voucher:order:status:{orderId} */
    public static final String ORDER_STATUS_KEY = "voucher:order:status:";
    /** 订单处理状态过期时间（分钟） */
    public static final Long ORDER_STATUS_TTL = 30L;

    // ---------- 个性化推荐 ----------
    /** 用户画像 ZSet：member=商铺类型ID，score=累计兴趣分 */
    public static final String RECOMMEND_PROFILE_KEY = "recommend:user:profile:";
    /** 用户画像过期时间（天） */
    public static final Long RECOMMEND_PROFILE_TTL = 30L;

    /** 已浏览商铺 Set */
    public static final String RECOMMEND_VIEWED_SHOP_KEY = "recommend:viewed:shop:";
    /** 已浏览博客 Set */
    public static final String RECOMMEND_VIEWED_BLOG_KEY = "recommend:viewed:blog:";
    /** 已浏览记录过期时间（天） */
    public static final Long RECOMMEND_VIEWED_TTL = 30L;

    /** 商铺推荐缓存 ZSet：member=商铺ID，score=推荐分 */
    public static final String RECOMMEND_SHOP_KEY = "recommend:user:shop:";
    /** 博客推荐缓存 ZSet：member=博客ID，score=推荐分 */
    public static final String RECOMMEND_BLOG_KEY = "recommend:user:blog:";
    /** 推荐缓存过期时间（分钟） */
    public static final Long RECOMMEND_CACHE_TTL = 30L;
}
