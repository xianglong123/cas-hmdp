package com.cas.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cas.dto.Result;
import com.cas.entity.VoucherOrder;
import com.cas.mapper.VoucherOrderMapper;
import com.cas.service.ISeckillVoucherService;
import com.cas.service.IVoucherOrderService;
import com.cas.utils.RedisIdWorker;
import com.cas.utils.UserHolder;
import com.rabbitmq.client.Channel;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;

import static com.cas.config.DirectRabbitConfig.DIRECT_EXCHANGE;
import static com.cas.config.DirectRabbitConfig.DIRECT_QUEUE;
import static com.cas.config.DirectRabbitConfig.DIRECT_ROUTING;

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

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderServiceImpl.class);

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RabbitTemplate rabbitTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @RabbitListener(queues = DIRECT_QUEUE)
    public void receiveA(Message message, Channel channel) throws IOException {
        String msg = new String(message.getBody());
        VoucherOrder order = JSON.parseObject(msg, VoucherOrder.class);
        log.info("异步下单成功：{},{}", new Date().toString(), msg);
        createVoucher(order);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @Override
    @Transactional
    public Result seckillVocher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 1、 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        // 2、 判断结果是0
        assert result != null;
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.1、不为0， 代表没有购买资格
        long orderId = redisIdWorker.nextId("order");
        // 2.2、为0，有购买资格
        // 3、返回订单id
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id
        voucherOrder.setId(orderId);
        // 用户id
        voucherOrder.setUserId(userId);
        // 代金劵id
        voucherOrder.setVoucherId(voucherId);
        // 交给队列
        rabbitTemplate.convertAndSend(DIRECT_EXCHANGE, DIRECT_ROUTING, JSON.toJSONString(voucherOrder));

        return Result.ok(orderId);
    }



    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucher(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        // 一个一单
        int count = query().eq("user_id", voucherOrder.getUserId()).eq("voucher_id", voucherId).count();
        if (count > 0) {
            log.debug("用户已经购买过一次");
            return ;
        }

        // 4、扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return ;
        }
        save(voucherOrder);
    }


//    @Override
//    @Transactional
//    public Result seckillVocher(Long voucherId) {
//        // 1、查询优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2、判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        // 3、判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//        // 库存不足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//
//        Long userId = UserHolder.getUser().getId();
//
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:");
//        // 获取锁
//        boolean isLock = lock.tryLock();
//        // 判断是否获取锁成功
//        if (!isLock) {
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            Result result = proxy.createVoucher(voucherId);
//            // 这里不能合并为一条语句，finally会将unlock提前到return之前执行，导致超卖
//            return result;
//        } finally {
//            lock.unlock();
//        }
//    }
}
