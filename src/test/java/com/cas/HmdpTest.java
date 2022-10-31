package com.cas;

import com.cas.entity.Shop;
import com.cas.service.impl.ShopServiceImpl;
import com.cas.utils.RedisUtil;
import com.cas.utils.RedisIdWorker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.cas.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * @author xiang_long
 * @version 1.0
 * @date 2022/10/25 4:32 下午
 * @desc
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class HmdpTest {

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisWorker;

    private static final ThreadPoolExecutor CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(300, 300, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(12), new ThreadPoolExecutor.DiscardPolicy());


    @Test
    public void testRedisWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        for (int i = 0; i < 300; i++) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                for (int j = 0; j < 100; j++) {
                    printBinary(redisWorker.nextId("order"));
                    System.out.println();
                }
                latch.countDown();
            });
        }
        long startTime = System.currentTimeMillis();
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("耗时：" + (end - startTime));
    }

    public static void printBinary(Long a){
        System.out.print(a + " --- ");
        for (int i = 80; i >= 0; i--){
            System.out.print(((a >> i) & 1));
        }
    }

    @Test
    public void testSaveShop() {
        Shop shop = shopService.getById(1L);
        redisUtil.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

}
