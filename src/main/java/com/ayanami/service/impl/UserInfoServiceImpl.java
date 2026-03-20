package com.ayanami.service.impl;

import com.ayanami.entity.UserInfo;
import com.ayanami.mapper.UserInfoMapper;
import com.ayanami.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
