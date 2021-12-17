package com.server.base.cache.converter;

/**
 * @author hanlipeng
 * @date 2019-07-15
 */
public interface KeyEncoder<K> {

    String encode(K key);

}
