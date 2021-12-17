package com.server.base.cache.entity;

import lombok.Data;

/**
 * @author hanlipeng
 * @date 2019-07-16
 */
@Data
public class CacheResult<T> {

    private boolean success;

    private T data;

    private long expireAt;
}
