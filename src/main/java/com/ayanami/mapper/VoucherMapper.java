package com.ayanami.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ayanami.entity.Voucher;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 */
public interface VoucherMapper extends BaseMapper<Voucher> {
   @Select("SELECT v.*,sv.begin_time,sv.end_time FROM tb_voucher v left join tb_seckill_voucher sv on v.id=sv.voucher_id WHERE shop_id = #{shopId} ")
    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
