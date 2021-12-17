package com.server.base.cache.converter;

import com.server.generic.util.JsonUtils;

/**
 * @author hanlipeng
 * @date 2019-07-16
 */
public class JsonKeyEncoder<T> implements KeyEncoder<T> {

    @Override
    public String encode(T key) {
        return JsonUtils.getStringJsonUtils().toJson(key);
    }
}
