package com.ayanami.service.impl;

import com.ayanami.entity.Blog;
import com.ayanami.mapper.BlogMapper;
import com.ayanami.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

}
