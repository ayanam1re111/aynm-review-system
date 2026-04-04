package com.ayanami.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.ayanami.dto.Result;
import com.ayanami.dto.UserDTO;
import com.ayanami.entity.Follow;
import com.ayanami.mapper.FollowMapper;
import com.ayanami.service.IFollowService;
import com.ayanami.utils.UserHolder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserServiceImpl userService;
    /**
     * 关注
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取登录用户
        Long userId= UserHolder.getUser().getId();
        String key="follows:"+userId;
        //1.判断到底是关注还是取关
        if(isFollow){
            //2.关注，新增数据
            Follow follow=new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            //存入数据库
            boolean isSuccess=save(follow);
            if(isSuccess){
                //把关注用户的id，放入redis的set集合用于以后查交集得出共同关注 sadd userId followerUserId
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else{
            //3.取关，删除delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>().
                    eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 取关
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        //1.获取登录用户
        Long userId=UserHolder.getUser().getId();
         //2.查询是否关注
        Integer count=query().eq("user_id",userId).eq("follow_user_id",followUserId).count();
         //3.判断
        return Result.ok(count>0);
    }

    /**
     * 查询共同关注
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;//当前用户
        //2.求交集
        String key2 = "follows:" + id;//目标用户
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            //无交集
            return Result.ok(Collections.emptyList());
        }
        //3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //4.查询用户
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);

    }

}
