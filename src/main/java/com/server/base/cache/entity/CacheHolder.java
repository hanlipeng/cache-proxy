package com.server.base.cache.entity;

import lombok.Data;

/**
 * @author hanlipeng
 * @date 2019-08-01
 */
@Data
public class CacheHolder<T> {

    private T data;

    private long expireAt;
}
