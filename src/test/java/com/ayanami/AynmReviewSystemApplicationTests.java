package com.ayanami;

import com.ayanami.entity.Shop;
import com.ayanami.service.impl.ShopServiceImpl;
import com.ayanami.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static com.ayanami.utils.RedisConstants.CACHE_SHOP_KEY;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest
class AynmReviewSystemApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;

    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY+1L,shop,10L,SECONDS);
    }


}
