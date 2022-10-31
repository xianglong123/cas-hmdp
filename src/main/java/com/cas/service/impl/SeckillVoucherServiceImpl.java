package com.cas.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cas.entity.SeckillVoucher;
import com.cas.mapper.SeckillVoucherMapper;
import com.cas.service.ISeckillVoucherService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

}
