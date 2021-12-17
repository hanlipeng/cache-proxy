package com.server.base.cache.converter;

import com.esotericsoftware.kryo.KryoException;
import com.server.base.cache.entity.CacheHolder;
import com.server.base.cache.exception.ConvertException;
import com.server.base.cache.util.KryoUtil;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author hanlipeng
 * @date 2019-07-15
 */
public class KryoValueConverter<IN> implements ValueConverter<IN> {

    private static final Map<Object, byte[]> CONST_MAP;

    static {
        CONST_MAP = new HashMap<>();
        byte[] nullObject = KryoUtil.writeToByteArray(null);
        byte[] emptyList = KryoUtil.writeObjectToByteArray(Collections.emptyList());
        CONST_MAP.put(null, nullObject);
        CONST_MAP.put(Collections.emptyList(), emptyList);

    }

    @Override
    public CacheHolder<IN> decode(byte[] value) throws ConvertException {
        try {
            return KryoUtil.readFromByteArray(value);
        } catch (KryoException e) {
            throw new ConvertException(e);
        }
    }

    @Override
    public byte[] encode(CacheHolder<IN> value) {
        if (CONST_MAP.containsKey(value)) {
            return CONST_MAP.get(value);
        }
        return KryoUtil.writeToByteArray(value);
    }
}
