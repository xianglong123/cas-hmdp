package com.cas.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cas.dto.Result;
import com.cas.entity.SeckillVoucher;
import com.cas.entity.VoucherOrder;
import com.cas.mapper.VoucherOrderMapper;
import com.cas.service.ISeckillVoucherService;
import com.cas.service.IVoucherOrderService;
import com.cas.utils.RedisIdWorker;
import com.cas.utils.RedisUtil;
import com.cas.utils.UserHolder;
import org.omg.CORBA.PRIVATE_MEMBER;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public Result seckillVocher(Long voucherId) {
        // 1、查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2、判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        // 3、判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        // 库存不足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();

        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 获取锁
        boolean isLock = lock.tryLock(1200);

        // 判断是否获取锁成功
        if (!isLock) {
            return Result.fail("不允许重复下单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucher(voucherId);
        } finally {
            lock.unLock();
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createVoucher(Long voucherId) {
        // 一个一单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            log.debug("用户已经购买过一次");
            return Result.fail("用户已经购买过一次");
        }

        // 4、扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }
        // 5、创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 用户id
        voucherOrder.setUserId(userId);
        // 代金劵id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 6、 但会订单ID
        return Result.ok(orderId);
    }
}
