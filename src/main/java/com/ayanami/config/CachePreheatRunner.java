package com.ayanami.config;

import cn.hutool.core.util.BooleanUtil;
import com.ayanami.entity.Shop;
import com.ayanami.service.IShopService;
import com.ayanami.utils.CacheClient;
import com.ayanami.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ayanami.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.ayanami.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * 启动时缓存预热：把商铺坐标写入 Redis GEO 索引，并按销量预热热门商铺缓存。
 * 预热失败只记录日志，不阻断应用启动。
 */
@Slf4j
@Component
public class CachePreheatRunner implements ApplicationRunner {

    /** 预热热门商铺的数量上限 */
    private static final int HOT_SHOP_LIMIT = 100;

    @Resource
    private IShopService shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            preheatShopGeo();
        } catch (Exception e) {
            log.error("商铺 GEO 索引预热失败", e);
        }
        try {
            preheatHotShop();
        } catch (Exception e) {
            log.error("热门商铺缓存预热失败", e);
        }
    }

    /**
     * 按商铺类型把经纬度写入 GEO 索引，供附近商铺检索使用。已存在的类型跳过，可重复执行。
     */
    private void preheatShopGeo() {
        Map<Long, List<Shop>> shopsByType = shopService.list().stream()
                .filter(shop -> shop.getTypeId() != null && shop.getX() != null && shop.getY() != null)
                .collect(Collectors.groupingBy(Shop::getTypeId));

        int warmed = 0;
        for (Map.Entry<Long, List<Shop>> entry : shopsByType.entrySet()) {
            String key = RedisConstants.SHOP_GEO_KEY + entry.getKey();
            if (BooleanUtil.isTrue(stringRedisTemplate.hasKey(key))) {
                continue;
            }
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(entry.getValue().size());
            for (Shop shop : entry.getValue()) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
            warmed += locations.size();
        }
        log.info("商铺 GEO 索引预热完成, 新增 {} 条", warmed);
    }

    /**
     * 按销量取前 N 个商铺提前写入缓存，避免冷启动时热点数据集中打到数据库。
     * 这里用普通 {@link CacheClient#set}，与读路径 queryWithMutex 的格式保持一致。
     */
    private void preheatHotShop() {
        List<Shop> hotShops = shopService.query()
                .orderByDesc("sold")
                .last("LIMIT " + HOT_SHOP_LIMIT)
                .list();
        for (Shop shop : hotShops) {
            cacheClient.set(CACHE_SHOP_KEY + shop.getId(), shop, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }
        log.info("热门商铺缓存预热完成, 共 {} 条", hotShops.size());
    }
}
