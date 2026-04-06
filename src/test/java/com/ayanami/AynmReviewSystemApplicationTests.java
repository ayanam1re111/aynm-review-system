package com.ayanami;

import com.ayanami.entity.Shop;
import com.ayanami.service.impl.ShopServiceImpl;
import com.ayanami.utils.CacheClient;
import com.ayanami.utils.RedisConstants;
import com.ayanami.utils.RedisIdWorker;
import org.apache.coyote.http11.filters.IdentityOutputFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.ayanami.utils.RedisConstants.CACHE_SHOP_KEY;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest
class AynmReviewSystemApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    private final ExecutorService es= Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);//并发计数器，用于让主线程等待所有子线程执行完成
        Runnable task=() ->{
            for(int i=0;i<100;i++){
                long id = redisIdWorker.nextId("order");
                System.out.println("id="+id);

            }
            latch.countDown();//计数器减一
        };
        long begin=System.currentTimeMillis();
        for(int i=0;i<300;i++){//发起300次任务提交给线程池
            es.submit(task);
        }
        latch.await();//让当前线程阻塞，直到计数器变为0
        long end=System.currentTimeMillis();
        System.out.println("time="+(end-begin));
    }


    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY+1L,shop,10L,SECONDS);
    }


    @Test
    void loadShopData(){
        //1.查询店铺信息
        List<Shop> list =shopService.list();
        //2.把店铺分组，按照typeId分组
        Map<Long,List<Shop>> map=list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.分批完成写入redis
        for(Map.Entry<Long,List<Shop>> entry : map.entrySet()){
        //3.1.获取类型id
            Long typeId=entry.getKey();
            String key= RedisConstants.SHOP_GEO_KEY+typeId;
        //3.2.获取同类型的店铺集合
            List<Shop> value=entry.getValue();
            //将每个店铺的经度维度ID封装起来存入列表（用于最后批量写入redis，效率更高）
            List<RedisGeoCommands.GeoLocation<String>> locations=new ArrayList<>(value.size());
        //3.3写入redis GEOADD key 经度 维度 member
            for(Shop shop:value){
                //单个写入，效率低：stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString()
                        ,new Point(shop.getX(),shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key,locations);

            }

    }





}
