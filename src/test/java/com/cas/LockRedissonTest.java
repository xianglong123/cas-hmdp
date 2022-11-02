package com.cas;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @author xiang_long
 * @version 1.0
 * @date 2022/10/31 4:04 下午
 * @desc
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class LockRedissonTest {

    private static final Logger log = LoggerFactory.getLogger(LockRedissonTest.class);

    @Resource
    private RedissonClient redissonClient;

    private RLock lock;

    @Before
    public void setUp() {
        lock = redissonClient.getLock("order");
    }

    @Test
    public void method1() throws InterruptedException {
        // 尝试获取锁
        boolean isLock = lock.tryLock(30L, TimeUnit.SECONDS);
        if (!isLock) {
            log.error("获取锁失败 .... 1");
            return ;
        }

        try {
            log.info("获取锁成功 ... 1");
            method2();
            log.info("开始执行业务 ... 1");
        } finally {
            log.warn("准备释放锁 ... 1");
            lock.unlock();
        }
    }

    private void method2() {
        // 尝试获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败 ... 2");
            return ;
        }
        try {
            log.info("获取锁成功 ... 2");
            log.info("开始执行业务 ... 2");
        } finally {
            log.warn("准备释放锁 ... 2");
            lock.unlock();
        }

    }


}
