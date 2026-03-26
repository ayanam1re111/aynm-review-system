package com.ayanami.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private String name;//业务名
    private static final String KEY_PREFIX="lock:";
    //每台虚拟机唯一标识
    private static final String ID_PREFIX= UUID.randomUUID().toString(true) +"-";
    private StringRedisTemplate stringRedisTemplate;
    public SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate) {
        this.name=name;
        this.stringRedisTemplate=stringRedisTemplate;
    }
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));//会去classpath下找文件
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 获取锁
     * @param timeoutSec
     * @return
     */
    @Override
        public boolean tryLock(long timeoutSec) {
        //获取线程标识
        String threadId=ID_PREFIX+Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX+name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);//用比较防止空指针异常，如果success是null，和true比较也返回false

    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        //调用LUA脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,//LUA解锁脚本
                Collections.singletonList(KEY_PREFIX+name),//传给LUA分布式锁的key
                ID_PREFIX+Thread.currentThread().getId());//传给LUA的ARGV[1]
    }
//
//    /**
//     * 释放锁
//     */
//    @Override
//    public void unlock() {
//        //获取线程标识
//        String threadId=ID_PREFIX+Thread.currentThread().getId();
//        //获取锁中的标识
//        String id=stringRedisTemplate.opsForValue().get(KEY_PREFIX+name);
//        //判断标识是否一致
//        if(threadId.equals(id)) {
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
