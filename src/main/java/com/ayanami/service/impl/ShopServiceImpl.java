package com.ayanami.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ayanami.dto.Result;
import com.ayanami.entity.Shop;
import com.ayanami.mapper.ShopMapper;
import com.ayanami.service.IShopService;
import com.ayanami.utils.CacheClient;
import com.ayanami.utils.RedisData;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ayanami.utils.RedisConstants;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.ayanami.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.ayanami.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 查找以及缓存商铺
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
//        //缓存穿透
//        Shop shop=cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,id2->getById(id2),CACHE_SHOP_TTL,TimeUnit.MINUTES);

//        //互斥锁解决缓存击穿
        Shop shop=cacheClient.queryWithMutex(CACHE_SHOP_KEY,"shop",id,Shop.class,this::getById,20L,TimeUnit.SECONDS);

//        //逻辑过期解决缓存击穿
//        Shop shop=cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,20L,TimeUnit.SECONDS);
        if(shop==null){
            return Result.fail("店铺不存在");
        }
        //7.返回
        return Result.ok(shop);
    }










    /**
     * 更新商铺信息，删除缓存
     * @param shop
     * @return
     */
    @Override
    //事务
    @Transactional
    public Result update(Shop shop) {
        //判断店铺是否存在
        Long id=shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
        //1.id不为空，更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

}
