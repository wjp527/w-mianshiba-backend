package com.wjp.mianshiba.manager;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.IntegerCodec;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;


/**
 * 通用计数器（可用于实现频率统计、限流、封禁等功能）
 */
@Slf4j
@Service
public class CounterManager {

    /**
     * 操作 Redis的客户端 【后面还可以操作 Lua脚本】
     */
    @Resource
    private RedissonClient redissonClient;

    /**
     * 增加返回值并计数, 默认统计一分钟内的计数结果
     *
     * @param key                     缓存键
     * @return
     */
    public long incrAndGetCounter(String key) {
        return incrAndGetCounter(key, 1, TimeUnit.MINUTES);
    }


    /**
     * 增加返回值并计数
     *
     * @param key                     缓存键
     * @param timeInterval            时间间隔
     * @param timeUnit                时间间隔单位
     * @return
     */
    public long incrAndGetCounter(String key, int timeInterval, TimeUnit timeUnit) {
        long expirationTimeInSeconds;

        // 根据时间力度生成 Redis key
        switch (timeUnit) {
            case SECONDS:
                expirationTimeInSeconds = timeInterval;
                break;
            case MINUTES:
                expirationTimeInSeconds = timeInterval * 60;
                break;
            case HOURS:
                expirationTimeInSeconds = timeInterval * 60 * 60;
                break;
            default:
                throw new IllegalArgumentException("不支持该时间单位");
        }
        return incrAndGetCounter(key, timeInterval, timeUnit, expirationTimeInSeconds);
    }

    /**
     * 增加返回值并计数
     *
     * @param key                     缓存键
     * @param timeInterval            时间间隔
     * @param timeUnit                时间间隔单位
     * @param expirationTimeInSeconds 计数器缓存过期时间
     * @return
     */
    public long incrAndGetCounter(String key, int timeInterval, TimeUnit timeUnit, long expirationTimeInSeconds) {
        // 如果key为空，直接返回0
        if (StrUtil.isBlank(key)) {
            return 0;
        }

        // 根据时间力度生成 Redis key
        long timeFactor;
        switch (timeUnit) {
            case SECONDS:
                timeFactor = Instant.now().getEpochSecond() / timeInterval;
                break;
            case MINUTES:
                timeFactor = Instant.now().getEpochSecond() / timeInterval / 60;
                break;
            case HOURS:
                timeFactor = Instant.now().getEpochSecond() / timeInterval / 60 / 60;
                break;
            default:
                throw new IllegalArgumentException("不支持该时间单位");
        }

        // 生成 Redis key
        String redisKey = key + ":" + timeFactor;

        // Lua脚本
        String luaScript =
                "if redis.call('exists', KEYS[1]) == 1 then " +
                        "  return redis.call('incr', KEYS[1]); " +
                        "else " +
                        "  redis.call('set', KEYS[1], 1); " +
                        "  redis.call('expire', KEYS[1], 180); " +  // 设置 180 秒过期时间
                        "  return 1; " +
                        "end";
        // 获取 Lua脚本 的实例
        // IntegerCodec.INSTANCE
        RScript script = redissonClient.getScript(IntegerCodec.INSTANCE);
        // 执行 Lua脚本
        Object countObj = script.eval(
                RScript.Mode.READ_WRITE, // 读写
                luaScript, // Lua脚本
                RScript.ReturnType.INTEGER, // 返回值类型
                Collections.singletonList(redisKey), // Redis key
                expirationTimeInSeconds // 过期时间
        );

        return (long) countObj;

    }


}
