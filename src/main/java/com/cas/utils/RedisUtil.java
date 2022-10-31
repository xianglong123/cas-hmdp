package com.cas.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.cas.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.cas.utils.RedisConstants.LOCK_SHOP_KEY;

@Service
public class RedisUtil {
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 设置 String 类型 key-value
     *
     * @param key
     * @param value
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1、从redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)) {
            // 2、存在，直接返回
            return JSONUtil.toBean(json, type);
        }

        if (json != null) {
            return null;
        }
        R r = dbFallback.apply(id);
        if (r == null) {
            set(key, "", time, unit);
            return null;
        }

        set(key, JSONUtil.toJsonStr(r), time, unit);
        return r;
    }

    private static final ThreadPoolExecutor CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(5, 5, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(12), new ThreadPoolExecutor.DiscardPolicy());

    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> function, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1、从redis查询商铺缓存
        String shopJosn = stringRedisTemplate.opsForValue().get(key);

        // 2、判断缓存是否存在
        if (StrUtil.isBlank(shopJosn)) {
            // 3、不存在，直接返回
            return null;
        }

        // 4、命中，把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJosn, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5、判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1、未过期 直接返回店铺信息
            return r;
        }
        // 5.2、过期，缓存重建
        // 6、缓存重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        // 6.2 判断是否获取锁成功
        if (tryLock(lockKey)) {
            // 6.3 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R r1 = function.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }

        // 返回数据
        return r;
    }

    private boolean tryLock(String key) {
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "", 10L, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }


    public boolean setIfAbsent(String key, String val, Long time, TimeUnit unit) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, val, time, unit);
    }

}
