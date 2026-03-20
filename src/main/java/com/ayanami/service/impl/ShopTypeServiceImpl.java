package com.ayanami.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.ayanami.dto.Result;
import com.ayanami.entity.ShopType;
import com.ayanami.mapper.ShopTypeMapper;
import com.ayanami.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ayanami.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>

 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查找以及缓存商户类型
      * @return
     */
    @Override
    public Result queryList(Long id) {
        //1.从redis中查找服务类型缓存
        String key= RedisConstants.CACHE_SHOPTYPE_KEY;
        List<String> shopTypeJson=stringRedisTemplate.opsForList().range(key, 0, -1);
        
        //2.判断是否存在
        List<ShopType> shopTypeList=new ArrayList<>();
        if(CollectionUtil.isNotEmpty(shopTypeJson)){
            //3.若存在，直接返回服务类型对象给前端
            for(String t:shopTypeJson){
                ShopType shopType= JSONUtil.toBean(t,ShopType.class);
                shopTypeList.add(shopType);
            }
             return Result.ok(shopTypeList);
        }
        //4.若不存在，进入数据库查询
        shopTypeList=query().orderByAsc("sort").list();
        //5.判断数据库中是否存在该服务类型
        if(CollectionUtil.isEmpty(shopTypeList)){
            //6.不存在，返回错误
            return Result.fail("不存在该商户类型");
        }

        //7.存在，将服务类型写入redis缓存
        //7.1.序列化商户类型信息
        for(ShopType shopType:shopTypeList){
            String s=JSONUtil.toJsonStr(shopType);
            shopTypeJson.add(s);
        }
        //7.2.写入
        stringRedisTemplate.opsForList().rightPushAll(key,shopTypeJson);
        //8.返回
        return Result.ok(shopTypeList);

    }
}
