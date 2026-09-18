package com.ayanami.config;

import cn.hutool.core.util.StrUtil;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置。
 * 连接信息统一从 spring.redis.* 读取，避免与 application.yaml 各写一份导致不一致。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.redis.port:6379}")
    private int port;

    /** 留空表示 Redis 未设密码，此时不调用 setPassword */
    @Value("${spring.redis.password:}")
    private String password;

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port);
        if (StrUtil.isNotBlank(password)) {
            server.setPassword(password);
        }
        return Redisson.create(config);
    }
}
