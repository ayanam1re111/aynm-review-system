package com.ayanami.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ayanami.dto.LoginFormDTO;
import com.ayanami.dto.Result;
import com.ayanami.dto.UserDTO;
import com.ayanami.entity.User;
import com.ayanami.mapper.UserMapper;
import com.ayanami.service.IUserService;
import com.ayanami.utils.RedisConstants;
import com.ayanami.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.ayanami.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.ayanami.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    private static final String sesCode = "code";
    @Resource//注入工具类对象（用于操作字符串类型的redis数据）
    private StringRedisTemplate stringRedisTemplate;;

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.检验手机号
        if(RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //3.符合，生成验证码
        String code=RandomUtil.randomNumbers(6);
        //4.保存验证码到redis(phone前面加业务前缀以区分各个key)
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        //6.返回OK
        return Result.ok();
    }

    /**
     * 登录
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone=loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)) {
            //如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        //2.从redis校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);//获取验证码
        String code=loginForm.getCode();//用户输入的验证码
        if(cacheCode==null || !cacheCode.toString().equals(code)) {
        //3.不一致，报错
            return Result.fail("验证码错误");}
        //4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5.判断用户是否存在
        if(user == null){
            //6.不存在，创建新用户
            user=createUserWithPhone(phone);
        }

        //7.(无论之前是否存在手机号）保存用户信息到redis中
        //7.1.生成一个token，作为登录令牌
        String token= UUID.randomUUID().toString(true);
        //7.2.将User对象转为Hash存储
        UserDTO userDTO=BeanUtil.copyProperties(user,UserDTO.class);//将user的属性拷贝给userDTO
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()//自定义，将字段值都转为string，不然无法用stringRedisTemplate存入redis
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));//将userdto转为map，方便后续使用putAll存储

        //7.3.存储(key:token)
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+token,userMap);
        //设置token有效期，防止过多的存储
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token, RedisConstants.LOGIN_USER_TTL,TimeUnit.MINUTES);
        //8.返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user= new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        //2.保存用户
        save(user);
        return user;
    }
}
