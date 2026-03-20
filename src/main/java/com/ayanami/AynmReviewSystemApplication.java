package com.ayanami;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.ayanami.mapper")
@SpringBootApplication
public class AynmReviewSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(AynmReviewSystemApplication.class, args);
    }

}
