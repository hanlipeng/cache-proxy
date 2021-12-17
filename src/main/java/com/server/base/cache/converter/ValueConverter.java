package com.server.base.cache.converter;

import com.server.base.cache.entity.CacheHolder;
import com.server.base.cache.exception.ConvertException;

/**
 * @author hanlipeng
 * @date 2019-07-15
 */
public interface ValueConverter<IN> {

    CacheHolder<IN> decode(byte[] value) throws ConvertException;

    byte[] encode(CacheHolder<IN> value);
}
