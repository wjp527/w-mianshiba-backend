package com.wjp.mianshiba.constant;

public interface RedisConstant {

    /**
     * 用户签到记录的 Redis Key 前缀
     */
    String USER_SIGN_IN_REDIS_KEY_PREFIX = "mianshiba:user:signIn:";

    /**
     * 用户签到记录的 Redis key
     * @param year
     * @param userId
     * @return
     */
    static String getUserSignInRedisKeyPrefix(int year, long userId) {
        return String.format("%s:%s:%s", USER_SIGN_IN_REDIS_KEY_PREFIX, year, userId);
    }


}
