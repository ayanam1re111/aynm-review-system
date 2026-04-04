package com.ayanami.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.ayanami.dto.Result;
import com.ayanami.dto.UserDTO;
import com.ayanami.entity.Blog;
import com.ayanami.entity.User;
import com.ayanami.mapper.BlogMapper;
import com.ayanami.service.IBlogService;
import com.ayanami.service.IUserService;
import com.ayanami.utils.SystemConstants;
import com.ayanami.utils.UserHolder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ayanami.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            // 第一步：把传进去的发布者user_id 替换成 昵称 + 头像
            this.queryBlogUser(blog);
            // 第二步：判断当前登录用户是否点赞了这篇博客
            this.isBlogLiked(blog);
        });
        return Result.ok(records);

    }

    @Override
    public Result queryBlogById(Long id) {
        //1.查询blog
        Blog blog=getById(id);
        if(blog==null){
            return Result.fail("笔记不存在");
        }
        //2.查询blog有关的用户
        queryBlogUser(blog);
        //3.查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        //1.获取登录用户
        UserDTO user= UserHolder.getUser();
        if(user == null){
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        //2.判断当前登录用户是否已经点赞
        String key=BLOG_LIKED_KEY+blog.getId();
        Double score= stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!=null);

    }

    private void queryBlogUser(Blog blog) {
        // 1. 从博客对象中取出发布者的用户ID
        Long userId = blog.getUserId();
        // 2. 根据用户ID，调用用户服务查询完整的用户信息
        User user = userService.getById(userId);
        // 3. 把查询到的用户昵称，设置到博客对象的name字段
        blog.setName(user.getNickName());
        // 4. 把查询到的用户头像，设置到博客对象的icon字段
        blog.setIcon(user.getIcon());
    }

    /**
     * 点赞业务
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //1.获取登录用户
        Long userId= UserHolder.getUser().getId();
        //2.判断当前登录用户是否已经点赞
        String key=BLOG_LIKED_KEY+id;//blog ID
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score==null) {
            //3.如果未点赞，可以点赞
            //3.1.数据库点赞数+1
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
            //3.2.保存用户到Redis的set集合 zadd key value score
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else {
            //4.如果已点赞，取消点赞
            //4.1.数据库点赞-1
            boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
            //4.2.把用户从Redis的set集合移除
            if(isSuccess){
            stringRedisTemplate.opsForZSet().remove(key,userId.toString());
        }}

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key=BLOG_LIKED_KEY+id;
        //1.查询top5的点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5==null || top5.isEmpty()){
            
            return Result.ok(Collections.emptyList());
        }
        //2.解析其中用户ID
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr= StrUtil.join(",",ids);
        //3.根据用户ID查询用户
        List<UserDTO> userDTOS = userService.query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list().stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        //4.返回
        return Result.ok(userDTOS);
    }

}
