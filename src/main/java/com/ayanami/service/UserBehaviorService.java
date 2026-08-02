package com.ayanami.service;

/**
 * 用户行为采集服务：记录行为对应的商铺类型兴趣分。
 */
public interface UserBehaviorService {

    /** 记录浏览商铺 */
    void recordViewShop(Long userId, Long shopId);

    /** 记录浏览博客 */
    void recordViewBlog(Long userId, Long blogId);

    /** 记录点赞博客 */
    void recordLikeBlog(Long userId, Long blogId);

    /** 记录关注博主 */
    void recordFollow(Long userId, Long followUserId);

    /** 记录搜索商铺 */
    void recordSearch(Long userId, Long typeId);

    /** 记录秒杀下单 */
    void recordOrder(Long userId, Long voucherId);
}
