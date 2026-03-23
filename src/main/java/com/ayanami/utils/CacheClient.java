package com.ayanami.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ayanami.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.ayanami.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.ayanami.utils.RedisConstants.CACHE_SHOP_TTL;

@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将java对象序列化到redis的string类型中，并指定TTL
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    /**
     * 将java对象序列化到redis的string类型中，并指定逻辑TTL
     * @param key
     * @param value
     * @param LogicalTTL 逻辑TTL
     * @param unit
     */
    public void setWithLogicalExpire(String key,Object value,Long LogicalTTL,TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(LogicalTTL)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 根据key查询缓存，并反序列化为指定类型，利用空值解决缓存穿透
     * @param keyPrefix  缓存key前缀
     * @param id         查询的使用的id
     * @param type       返回值类型
     * @param dbFallback 查询业务逻辑函数
     * @param time       key的TTL
     * @param unit       TTL对应的单位
     * @param <R>        返回值泛型
     * @param <ID>       id泛型
     * @return 返回最终查询的数据
     */
    public <R,ID> R queryWithPassThrough(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit unit){
        //1.从redis查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(json)){
            //3.存在，直接返回
            return JSONUtil.toBean(json, type);//转为java对象

        }
        //解决缓存穿透
        //判断命中的是否是空值
        if(json !=null){
            //返回一个错误信息
            return null;
        }
        //4.不存在，根据id查询数据库
        R r=dbFallback.apply(id);
        //5.不存在，返回错误
        if(r==null){
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.存在，写入redis
        this.set(key,r,time,unit);
        //7.返回
        return r;
    }


    /**
     * 创建线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    /**
     *  根据key查询缓存，并反序列化为指定类型，利用逻辑过期解决缓存击穿
     * @param id
     * @return
     */
    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id,Class<R> type,Function<ID,R> dbFallback, Long time,TimeUnit unit){
        //1.从redis查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isBlank(json)){
            //3.未命中，直接返回
            return null;
        }
        //4.若命中则判断过期时间，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);//这里只给data定义为了object类型，json实际上将其反序列化为JSONObject类型
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            //5.1.未过期，直接返回店铺信息
            return r;
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
                    //查数据库
                    R r1 = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }

            });
        }

        //6.4.返回过期商铺信息
        return r;

    }


    /**
     * 根据key查询缓存，并反序列化为指定类型，利用互斥锁解决缓存击穿，同时利用缓存空值解决了缓存穿透
     * @param keyPrefix
     * @param lockKeyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time key的TTL
     * @param unit
     * @return
     * @param <ID>
     * @param <R>
     */
    public <ID,R> R queryWithMutex(String keyPrefix,String lockKeyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit unit){
        //1.从redis查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(json)){
            //3.存在，直接返回
            return JSONUtil.toBean(json, type);//转为java对象

        }
        //判断命中的是否是空值
        if(json !=null){
            //返回一个错误信息
            return null;
        }
        //4.实现缓存重建
        //4.1.获取互斥锁
        String lockKey="lock:"+lockKeyPrefix+id;
        R r= null;
        try {
            boolean isLock=tryLock(lockKey);
            //4.2.判断是否获取成功
            if(!isLock){//4.3.若失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix,lockKeyPrefix,id,type,dbFallback,time,unit);//做递归

            }
            //4.4.成功，根据id查询数据库
            r = dbFallback.apply(id);
            Thread.sleep(200);
            //5.不存在，返回错误
            if(r==null){
                stringRedisTemplate.opsForValue().set(key,"",time, unit);
                return null;
            }
            //6.存在，写入redis
           this.set(key,r,time,unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally{
        //7.释放互斥锁
        unlock(lockKey);}
        //8.返回
        return r;
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


}

