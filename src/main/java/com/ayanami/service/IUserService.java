package com.ayanami.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ayanami.dto.LoginFormDTO;
import com.ayanami.dto.Result;
import com.ayanami.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);
}
