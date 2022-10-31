package com.cas.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cas.dto.Result;
import com.cas.entity.VoucherOrder;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVocher(Long voucherId);

    Result createVoucher(Long voucherId);
}
