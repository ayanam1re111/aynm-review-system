package com.ayanami.controller;

import cn.hutool.core.lang.UUID;
import com.ayanami.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/test")
public class TokenTestController {
    //批量生成token接口
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @GetMapping("/token-batch")
    public void batchToken(@RequestParam(defaultValue="500") int count, HttpServletResponse response) throws IOException {
        //1.设置响应头，让浏览器下载文件
        response.setContentType("text/plain;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=tokens.txt");

        // 2. 获取输出流
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));

        for(int i=1;i<=count;i++){
            //生成一个token，作为登录令牌
            String token= UUID.randomUUID().toString(true);
            //拼接redisKey
            String key="login:token:"+token;
            //将用户信息存入redis
            Map<String,String> userMap=new HashMap<>();
            userMap.put("id",i+"");
            userMap.put("nickname","测试用户"+i);
            //存入reidis
            stringRedisTemplate.opsForHash().putAll(key,userMap);

            //token写入文件
            writer.write(token);
            writer.newLine();

        }
        //刷新关闭
        writer.flush();
        writer.close();

    }

}
