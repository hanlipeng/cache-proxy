package com.server.base.cache.util;

import com.server.base.cache.entity.CacheHolder;

/**
 * @author hanlipeng
 * @date 2019-07-16
 */
public final class CacheHolders {

    public static <T> CacheHolder<T> init(T value) {
        return buildResult(true, value, 0);
    }

    public static <T> CacheHolder<T> init(T value, long expireAt) {
        return buildResult(true, value, expireAt);
    }

    private static <T> CacheHolder<T> buildResult(boolean success, T value, long expireAt) {
        CacheHolder<T> result = new CacheHolder<>();
        result.setData(value);
        result.setExpireAt(expireAt);
        return result;
    }
}
