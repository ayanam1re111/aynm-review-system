package com.ayanami.service.impl;

import com.ayanami.entity.Blog;
import com.ayanami.entity.Shop;
import com.ayanami.entity.Voucher;
import com.ayanami.service.IBlogService;
import com.ayanami.service.IShopService;
import com.ayanami.service.IVoucherService;
import com.ayanami.service.UserBehaviorService;
import com.ayanami.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.List;

/**
 * 用户行为采集：记录用户对商铺类型的兴趣分到 Redis 画像，并记录已浏览内容。
 * 未登录或目标不存在时静默跳过，不影响主业务。
 */
@Slf4j
@Service
public class UserBehaviorServiceImpl implements UserBehaviorService {

    // 各行为权重：浏览/搜索 1，点赞/关注 2，下单 4
    private static final double WEIGHT_VIEW = 1;
    private static final double WEIGHT_SEARCH = 1;
    private static final double WEIGHT_LIKE = 2;
    private static final double WEIGHT_FOLLOW = 2;
    private static final double WEIGHT_ORDER = 4;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopService shopService;
    @Resource
    private IBlogService blogService;
    @Resource
    private IVoucherService voucherService;

    @Override
    public void recordViewShop(Long userId, Long shopId) {
        if (userId == null || shopId == null) {
            return;
        }
        Long typeId = resolveShopTypeId(shopId);
        if (typeId == null) {
            return;
        }
        incrProfile(userId, typeId, WEIGHT_VIEW);
        addViewed(userId, RedisConstants.RECOMMEND_VIEWED_SHOP_KEY, shopId.toString());
    }

    @Override
    public void recordViewBlog(Long userId, Long blogId) {
        if (userId == null || blogId == null) {
            return;
        }
        Long typeId = resolveBlogTypeId(blogService.getById(blogId));
        if (typeId == null) {
            return;
        }
        incrProfile(userId, typeId, WEIGHT_VIEW);
        addViewed(userId, RedisConstants.RECOMMEND_VIEWED_BLOG_KEY, blogId.toString());
    }

    @Override
    public void recordLikeBlog(Long userId, Long blogId) {
        if (userId == null || blogId == null) {
            return;
        }
        Long typeId = resolveBlogTypeId(blogService.getById(blogId));
        if (typeId == null) {
            return;
        }
        incrProfile(userId, typeId, WEIGHT_LIKE);
    }

    @Override
    public void recordFollow(Long userId, Long followUserId) {
        if (userId == null || followUserId == null) {
            return;
        }
        // 按被关注用户最近发布的博客累计其类型兴趣
        List<Blog> blogs = blogService.query().eq("user_id", followUserId)
                .orderByDesc("create_time")
                .last("LIMIT 5")
                .list();
        for (Blog blog : blogs) {
            Long typeId = resolveBlogTypeId(blog);
            if (typeId != null) {
                incrProfile(userId, typeId, WEIGHT_FOLLOW);
            }
        }
    }

    @Override
    public void recordSearch(Long userId, Long typeId) {
        if (userId == null || typeId == null) {
            return;
        }
        incrProfile(userId, typeId, WEIGHT_SEARCH);
    }

    @Override
    public void recordOrder(Long userId, Long voucherId) {
        if (userId == null || voucherId == null) {
            return;
        }
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null || voucher.getShopId() == null) {
            return;
        }
        Long typeId = resolveShopTypeId(voucher.getShopId());
        if (typeId == null) {
            return;
        }
        incrProfile(userId, typeId, WEIGHT_ORDER);
    }

    // ==================== 私有工具方法 ====================

    /**
     * 累加用户对商铺类型的兴趣分。
     */
    private void incrProfile(Long userId, Long typeId, double weight) {
        try {
            String key = RedisConstants.RECOMMEND_PROFILE_KEY + userId;
            stringRedisTemplate.opsForZSet().incrementScore(key, typeId.toString(), weight);
            stringRedisTemplate.expire(key, Duration.ofDays(RedisConstants.RECOMMEND_PROFILE_TTL));
        } catch (Exception e) {
            log.error("记录用户行为画像失败, userId={}, typeId={}", userId, typeId, e);
        }
    }

    /**
     * 记录用户已浏览内容，用于推荐过滤。
     */
    private void addViewed(Long userId, String keyPrefix, String targetId) {
        try {
            String key = keyPrefix + userId;
            stringRedisTemplate.opsForSet().add(key, targetId);
            stringRedisTemplate.expire(key, Duration.ofDays(RedisConstants.RECOMMEND_VIEWED_TTL));
        } catch (Exception e) {
            log.error("记录用户已浏览内容失败, userId={}, key={}", userId, keyPrefix, e);
        }
    }

    /**
     * 商铺 id → 商铺类型 id
     */
    private Long resolveShopTypeId(Long shopId) {
        if (shopId == null) {
            return null;
        }
        Shop shop = shopService.getById(shopId);
        return shop == null ? null : shop.getTypeId();
    }

    /**
     * 博客 → 商铺类型 id（通过博客关联的商铺）
     */
    private Long resolveBlogTypeId(Blog blog) {
        if (blog == null) {
            return null;
        }
        return resolveShopTypeId(blog.getShopId());
    }
}
