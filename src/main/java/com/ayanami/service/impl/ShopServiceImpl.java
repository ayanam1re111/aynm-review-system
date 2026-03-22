package com.ayanami.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ayanami.dto.Result;
import com.ayanami.entity.Shop;
import com.ayanami.mapper.ShopMapper;
import com.ayanami.service.IShopService;
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

    /**
     * 查找以及缓存商铺
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop=queryWithPassThrough(id);

//        //互斥锁解决缓存击穿
//        Shop shop=queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop=queryWithLogicalExpire(id);
        if(shop==null){
            return Result.fail("店铺不存在");
        }
        //7.返回
        return Result.ok(shop);
    }

    /**
     * 创建线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    /**
     * 逻辑过期解决缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id){
        //1.从redis查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isBlank(shopJson)){
            //3.未命中，直接返回
            return null;
        }
        //4.若命中则判断过期时间，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);//这里只给data定义为了object类型，json实际上将其反序列化为JSONObject类型
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            //5.1.未过期，直接返回店铺信息
            return shop;
        }
        //5.2.已过期，需要缓存重建
        //6.缓存重建
        //6.1.获取互斥锁
        String lockKey=RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock=tryLock(lockKey);
        //6.2.判断是否获取成功
        if(isLock){
            //6.3.成功，开启一个独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }

            });
        }

        //6.4.返回过期商铺信息
        return shop;

    }

    /**
     * 互斥锁解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id){
        //1.从redis查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);//转为java对象

        }
        //判断命中的是否是空值
        if(shopJson !=null){
            //返回一个错误信息
            return null;
        }
        //4.实现缓存重建
        //4.1.获取互斥锁
        String lockKey="lock:shop:"+id;
        Shop shop= null;
        try {
            boolean isLock=tryLock(lockKey);
            //4.2.判断是否获取成功
            if(!isLock){//4.3.若失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);//做递归

            }

            //4.4.成功，根据id查询数据库
            shop = getById(id);
            Thread.sleep(200);
            //5.不存在，返回错误
            if(shop==null){
                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6.存在，写入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally{
        //7.释放互斥锁
        unlock(lockKey);}
        //8.返回
        return shop;
    }

    /**
     * 封装解决缓存穿透的查询以及缓存商铺方法
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){
        //1.从redis查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);//转为java对象

        }
        //解决缓存穿透
        //判断命中的是否是空值
        if(shopJson !=null){
            //返回一个错误信息
            return null;
        }
        //4.不存在，根据id查询数据库
        Shop shop=getById(id);
        //5.不存在，返回错误
        if(shop==null){
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.存在，写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //7.返回
        return shop;
    }

    /**
     * 拿互斥锁方法
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);//拆箱成基本boolean类型
    }

    /**
     * 释放锁
     * @param key
     */
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 写入缓存与逻辑过期时间
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id,Long expireSeconds ) throws InterruptedException {
        //1.查询店铺数据
        Shop shop=getById(id);
        Thread.sleep(1000);//模拟该缓存重建存在一定延迟，易引发缓存击穿问题
        //2.封装逻辑过期时间
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
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
