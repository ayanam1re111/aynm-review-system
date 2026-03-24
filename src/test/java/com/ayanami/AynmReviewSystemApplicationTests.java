package com.ayanami;

import com.ayanami.entity.Shop;
import com.ayanami.service.impl.ShopServiceImpl;
import com.ayanami.utils.CacheClient;
import com.ayanami.utils.RedisIdWorker;
import org.apache.coyote.http11.filters.IdentityOutputFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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


}
