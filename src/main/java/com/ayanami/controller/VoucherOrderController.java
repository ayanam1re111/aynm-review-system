package com.ayanami.controller;


import com.ayanami.dto.Result;
import com.ayanami.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>

 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;

    /**
     * 秒杀下单功能
     * @param voucherId
     * @return
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {

        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 查询秒杀订单异步处理状态（PROCESSING / SUCCESS / FAILED）
     * @param orderId 订单 ID（秒杀接口返回的 orderId）
     * @return 订单处理状态
     */
    @GetMapping("status/{orderId}")
    public Result queryOrderStatus(@PathVariable("orderId") Long orderId) {

        return voucherOrderService.queryOrderStatus(orderId);
    }
}
