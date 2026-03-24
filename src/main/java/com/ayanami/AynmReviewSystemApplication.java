package com.ayanami;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.ayanami.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class AynmReviewSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(AynmReviewSystemApplication.class, args);
    }

}
