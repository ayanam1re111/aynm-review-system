package com.ayanami.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ayanami.dto.Result;
import com.ayanami.dto.UserDTO;
import com.ayanami.entity.Blog;
import com.ayanami.entity.Follow;
import com.ayanami.entity.Shop;
import com.ayanami.entity.User;
import com.ayanami.service.IBlogService;
import com.ayanami.service.IFollowService;
import com.ayanami.service.IRecommendService;
import com.ayanami.service.IShopService;
import com.ayanami.service.IUserService;
import com.ayanami.utils.RedisConstants;
import com.ayanami.utils.UserHolder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 个性化推荐：基于用户画像的轻量多因子加权推荐，结果缓存到 Redis ZSet。
 * 商铺推荐分 = 兴趣匹配×0.40 + 热度×0.25 + 距离×0.20 + 评分×0.10 + 时效×0.05
 * 博客推荐分 = 兴趣匹配×0.40 + 点赞热度×0.25 + 关注关系×0.20 + 新鲜度×0.15
 */
@Slf4j
@Service
public class RecommendServiceImpl implements IRecommendService {

    /** 每个偏好类型的候选数 */
    private static final int CANDIDATE_PER_TYPE = 10;
    /** 热门候选数 */
    private static final int HOT_CANDIDATE = 20;

    // ---------- 商铺评分权重 ----------
    private static final double W_INTEREST = 0.40;
    private static final double W_POPULAR = 0.25;
    private static final double W_DISTANCE = 0.20;
    private static final double W_SCORE = 0.10;
    private static final double W_FRESH = 0.05;

    // ---------- 博客评分权重 ----------
    private static final double W_LIKE_HEAT = 0.25;
    private static final double W_AUTHOR_FOLLOW = 0.20;
    private static final double W_BLOG_FRESH = 0.15;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopService shopService;
    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;

    // ==================== 商铺推荐 ====================

    @Override
    public Result queryRecommendShops(Double x, Double y, Integer current, Integer pageSize) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 未登录返回热门
            return hotShops(current, pageSize);
        }
        Long userId = user.getId();
        // 缓存为空则重新计算
        String cacheKey = RedisConstants.RECOMMEND_SHOP_KEY + userId;
        Long size = stringRedisTemplate.opsForZSet().zCard(cacheKey);
        if (size == null || size == 0) {
            refreshShopRecommend(userId, x, y);
        }
        // 分页读取推荐
        int from = (current - 1) * pageSize;
        int end = current * pageSize - 1;
        Set<String> idSet = stringRedisTemplate.opsForZSet().reverseRange(cacheKey, from, end);
        List<Shop> shops = new ArrayList<>();
        if (idSet != null && !idSet.isEmpty()) {
            List<Long> ids = idSet.stream().map(Long::valueOf).collect(Collectors.toList());
            String idStr = StrUtil.join(",", ids);
            shops = shopService.query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        }
        // 不足时用热门补足
        if (shops.size() < pageSize) {
            fillWithHotShops(shops, pageSize);
        }
        return Result.ok(shops);
    }

    /**
     * 重新计算商铺推荐并回填缓存。
     */
    private void refreshShopRecommend(Long userId, Double x, Double y) {
        // 用户偏好 Top3 类型
        Set<String> topTypes = stringRedisTemplate.opsForZSet()
                .reverseRange(RedisConstants.RECOMMEND_PROFILE_KEY + userId, 0, 2);

        // 候选：偏好类型商铺 + 热门商铺
        List<Shop> candidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        if (topTypes != null && !topTypes.isEmpty()) {
            for (String typeId : topTypes) {
                List<Shop> list = shopService.query().eq("type_id", typeId)
                        .orderByDesc("sold").last("LIMIT " + CANDIDATE_PER_TYPE).list();
                for (Shop s : list) {
                    if (seen.add(s.getId())) {
                        candidates.add(s);
                    }
                }
            }
        }
        List<Shop> hot = shopService.query().orderByDesc("sold").last("LIMIT " + HOT_CANDIDATE).list();
        for (Shop s : hot) {
            if (seen.add(s.getId())) {
                candidates.add(s);
            }
        }

        // 过滤已浏览内容
        Set<String> viewed = stringRedisTemplate.opsForSet()
                .members(RedisConstants.RECOMMEND_VIEWED_SHOP_KEY + userId);

        // 打分并写入缓存
        ZSetOperations<String, String> zset = stringRedisTemplate.opsForZSet();
        String cacheKey = RedisConstants.RECOMMEND_SHOP_KEY + userId;
        for (Shop shop : candidates) {
            if (viewed != null && viewed.contains(shop.getId().toString())) {
                continue;
            }
            double score = scoreShop(shop, topTypes, x, y);
            zset.add(cacheKey, shop.getId().toString(), score);
        }
        stringRedisTemplate.expire(cacheKey, Duration.ofMinutes(RedisConstants.RECOMMEND_CACHE_TTL));
    }

    /**
     * 商铺评分，各因子归一化到 0~1。
     */
    private double scoreShop(Shop shop, Set<String> topTypes, Double x, Double y) {
        // 类型是否命中用户偏好
        double interest = topTypes != null && topTypes.contains(shop.getTypeId().toString()) ? 1.0 : 0.0;
        // 销量热度（100 封顶）
        double popular = shop.getSold() == null ? 0.0 : Math.min(shop.getSold() / 100.0, 1.0);
        // 距离（10km 内线性衰减，无坐标给中性分）
        double distanceScore;
        if (x != null && y != null && shop.getX() != null && shop.getY() != null) {
            double km = haversineKm(y, x, shop.getY(), shop.getX());
            distanceScore = Math.max(0.0, 1.0 - km / 10.0);
        } else {
            distanceScore = 0.5;
        }
        // 评分（50 封顶）
        double rating = shop.getScore() == null ? 0.0 : Math.min(shop.getScore() / 50.0, 1.0);
        // 时效（180 天线性衰减）
        double fresh = freshness(shop.getCreateTime());
        return interest * W_INTEREST + popular * W_POPULAR + distanceScore * W_DISTANCE
                + rating * W_SCORE + fresh * W_FRESH;
    }

    // ==================== 博客推荐 ====================

    @Override
    public Result queryRecommendBlogs(Integer current, Integer pageSize) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 未登录返回热门
            return hotBlogs(current, pageSize);
        }
        Long userId = user.getId();
        // 缓存为空则重新计算
        String cacheKey = RedisConstants.RECOMMEND_BLOG_KEY + userId;
        Long size = stringRedisTemplate.opsForZSet().zCard(cacheKey);
        if (size == null || size == 0) {
            refreshBlogRecommend(userId);
        }
        // 分页读取推荐
        int from = (current - 1) * pageSize;
        int end = current * pageSize - 1;
        Set<String> idSet = stringRedisTemplate.opsForZSet().reverseRange(cacheKey, from, end);
        List<Blog> blogs = new ArrayList<>();
        if (idSet != null && !idSet.isEmpty()) {
            List<Long> ids = idSet.stream().map(Long::valueOf).collect(Collectors.toList());
            String idStr = StrUtil.join(",", ids);
            blogs = blogService.query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        }
        // 不足时用热门补足
        if (blogs.size() < pageSize) {
            fillWithHotBlogs(blogs, pageSize);
        }
        // 补充作者信息
        fillBlogAuthor(blogs);
        return Result.ok(blogs);
    }

    /**
     * 重新计算博客推荐并回填缓存。
     */
    private void refreshBlogRecommend(Long userId) {
        // 候选：关注博主的博客 + 热门博客 + 偏好类型关联博客
        List<Blog> candidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<Follow> follows = followService.query()
                .eq("user_id", userId).list();
        Set<String> followedIds = new HashSet<>();
        for (Follow follow : follows) {
            followedIds.add(follow.getFollowUserId().toString());
            List<Blog> list = blogService.query().eq("user_id", follow.getFollowUserId())
                    .orderByDesc("create_time").last("LIMIT " + CANDIDATE_PER_TYPE).list();
            for (Blog b : list) {
                if (seen.add(b.getId())) {
                    candidates.add(b);
                }
            }
        }
        List<Blog> hot = blogService.query().orderByDesc("liked").last("LIMIT " + HOT_CANDIDATE).list();
        for (Blog b : hot) {
            if (seen.add(b.getId())) {
                candidates.add(b);
            }
        }
        // 偏好类型下的商铺关联的博客
        Set<String> topTypes = stringRedisTemplate.opsForZSet()
                .reverseRange(RedisConstants.RECOMMEND_PROFILE_KEY + userId, 0, 2);
        if (topTypes != null && !topTypes.isEmpty()) {
            for (String typeId : topTypes) {
                List<Long> shopIds = shopService.query().eq("type_id", typeId)
                        .last("LIMIT 10").list().stream()
                        .map(Shop::getId).collect(Collectors.toList());
                if (shopIds.isEmpty()) {
                    continue;
                }
                List<Blog> list = blogService.query().in("shop_id", shopIds)
                        .orderByDesc("liked").last("LIMIT " + CANDIDATE_PER_TYPE).list();
                for (Blog b : list) {
                    if (seen.add(b.getId())) {
                        candidates.add(b);
                    }
                }
            }
        }

        // 过滤已浏览
        Set<String> viewed = stringRedisTemplate.opsForSet()
                .members(RedisConstants.RECOMMEND_VIEWED_BLOG_KEY + userId);

        // 打分并写入缓存
        ZSetOperations<String, String> zset = stringRedisTemplate.opsForZSet();
        String cacheKey = RedisConstants.RECOMMEND_BLOG_KEY + userId;
        for (Blog blog : candidates) {
            if (viewed != null && viewed.contains(blog.getId().toString())) {
                continue;
            }
            double score = scoreBlog(blog, topTypes, followedIds);
            zset.add(cacheKey, blog.getId().toString(), score);
        }
        stringRedisTemplate.expire(cacheKey, Duration.ofMinutes(RedisConstants.RECOMMEND_CACHE_TTL));
    }

    /**
     * 博客评分，各因子归一化到 0~1。
     */
    private double scoreBlog(Blog blog, Set<String> topTypes, Set<String> followedIds) {
        // 关联商铺类型是否命中用户偏好
        double interest = 0.0;
        if (blog.getShopId() != null) {
            Shop shop = shopService.getById(blog.getShopId());
            if (shop != null && topTypes != null && topTypes.contains(shop.getTypeId().toString())) {
                interest = 1.0;
            }
        }
        // 点赞热度（100 封顶）
        double likeHeat = blog.getLiked() == null ? 0.0 : Math.min(blog.getLiked() / 100.0, 1.0);
        // 作者是否被关注
        double authorFollow = blog.getUserId() != null && followedIds.contains(blog.getUserId().toString())
                ? 1.0 : 0.0;
        double fresh = freshness(blog.getCreateTime());
        return interest * W_INTEREST + likeHeat * W_LIKE_HEAT + authorFollow * W_AUTHOR_FOLLOW
                + fresh * W_BLOG_FRESH;
    }

    // ==================== 热门兜底 ====================

    /**
     * 未登录时返回热门商铺。
     */
    private Result hotShops(Integer current, Integer pageSize) {
        Page<Shop> page = shopService.query()
                .orderByDesc("sold")
                .page(new Page<>(current, pageSize));
        return Result.ok(page.getRecords(), page.getTotal());
    }

    /**
     * 未登录时返回热门博客。
     */
    private Result hotBlogs(Integer current, Integer pageSize) {
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, pageSize));
        fillBlogAuthor(page.getRecords());
        return Result.ok(page.getRecords(), page.getTotal());
    }

    /**
     * 为博客列表补充作者昵称与头像。
     */
    private void fillBlogAuthor(List<Blog> blogs) {
        for (Blog blog : blogs) {
            if (blog.getUserId() == null) {
                continue;
            }
            User author = userService.getById(blog.getUserId());
            if (author != null) {
                blog.setName(author.getNickName());
                blog.setIcon(author.getIcon());
            }
        }
    }

    /**
     * 用热门商铺补足推荐结果（去重）。
     */
    private void fillWithHotShops(List<Shop> shops, int pageSize) {
        Set<Long> existing = shops.stream().map(Shop::getId).collect(Collectors.toSet());
        Page<Shop> hot = shopService.query().orderByDesc("sold").page(new Page<>(1, pageSize * 3));
        for (Shop shop : hot.getRecords()) {
            if (shops.size() >= pageSize) {
                break;
            }
            if (existing.add(shop.getId())) {
                shops.add(shop);
            }
        }
    }

    /**
     * 用热门博客补足推荐结果（去重）。
     */
    private void fillWithHotBlogs(List<Blog> blogs, int pageSize) {
        Set<Long> existing = blogs.stream().map(Blog::getId).collect(Collectors.toSet());
        Page<Blog> hot = blogService.query().orderByDesc("liked").page(new Page<>(1, pageSize * 3));
        for (Blog blog : hot.getRecords()) {
            if (blogs.size() >= pageSize) {
                break;
            }
            if (existing.add(blog.getId())) {
                blogs.add(blog);
            }
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 时间新鲜度，180 天内线性衰减。
     */
    private double freshness(LocalDateTime createTime) {
        if (createTime == null) {
            return 0.5;
        }
        long days = ChronoUnit.DAYS.between(createTime, LocalDateTime.now());
        return Math.max(0.0, 1.0 - days / 180.0);
    }

    /**
     * 球面距离（Haversine），单位千米。
     */
    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
