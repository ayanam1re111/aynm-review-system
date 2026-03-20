package com.ayanami.service;

import com.ayanami.dto.Result;
import com.ayanami.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryList(Long id);
}
