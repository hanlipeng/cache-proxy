package com.server.base.cache.util;

import com.server.base.cache.entity.CacheResult;

/**
 * @author hanlipeng
 * @date 2019-07-16
 */
public final class CacheResults {

    public static <T> CacheResult<T> success(T value) {
        return buildResult(true, value, 0);
    }

    public static <T> CacheResult<T> success(T value, long expireAt) {
        return buildResult(true, value, expireAt);
    }

    public static <T> CacheResult<T> fail() {
        return buildResult(false, null, 0);
    }

    private static <T> CacheResult<T> buildResult(boolean success, T value, long expireAt) {
        CacheResult<T> result = new CacheResult<>();
        result.setData(value);
        result.setSuccess(success);
        result.setExpireAt(expireAt);
        return result;
    }
}
