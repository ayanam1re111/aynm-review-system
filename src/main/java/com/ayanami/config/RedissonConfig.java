package com.ayanami.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(){
        //配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.100.128:6379").setPassword("651324");
        //创建RedissionClient对象
        return Redisson.create(config);


    }
}
