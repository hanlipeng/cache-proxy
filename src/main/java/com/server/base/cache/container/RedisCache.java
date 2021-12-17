package com.server.base.cache.container;

import com.server.base.cache.converter.ValueConverter;
import com.server.base.cache.entity.CacheResult;
import com.server.base.cache.exception.ConvertException;
import com.server.base.cache.util.BatchInvokeUtils;
import com.server.base.cache.util.CacheHolders;
import com.server.base.cache.util.CacheResults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author hanlipeng
 * @date 2019-07-23
 */
@Slf4j
public class RedisCache<V> implements Cache<V> {
    /** 如果触发了mget的分批查询，则此值作为单次查询的最小元素个数 */
    private static final int mGetMinSize = 500;
    private static final int mGetMaxSize = 1000;

    private final RedisTemplate<String, byte[]> cache;

    private final ValueConverter<V> converter;

    public RedisCache(RedisConnectionFactory redisConnectionFactory, ValueConverter<V> converter) {
        this.cache = new RedisTemplate<>();
        cache.setConnectionFactory(redisConnectionFactory);
        this.converter = converter;
        cache.setValueSerializer(new RedisSerializer<byte[]>() {
            @Override
            public byte[] serialize(byte[] bytes) throws SerializationException {
                return bytes;
            }

            @Override
            public byte[] deserialize(byte[] bytes) throws SerializationException {
                return bytes;
            }
        });
        cache.setKeySerializer(new StringRedisSerializer());
        cache.afterPropertiesSet();
    }

    @Override
    public CacheResult<V> get(String key) {
        byte[] value = cache.opsForValue().get(key);
        CacheResult<V> result = CacheResults.fail();
        if (value != null) {
            try {
                result = warp(converter.decode(value));
            } catch (ConvertException e) {
                log.error("decode fail", e);
            }
        }
        return result;
    }

    @Override
    public Map<String, CacheResult<V>> getAll(Set<String> keys) {
        int keySize = keys.size();
        HashMap<String, CacheResult<V>> resultMap = new HashMap<>(keySize);

        if (keySize > mGetMaxSize + (mGetMaxSize >> 1) ) {
            if (log.isDebugEnabled()) {
                log.debug("调用Redis的mget命令时key的数量过多（共{}个），已优化为分批查", keySize);
            }
            BatchInvokeUtils.batchInvokeBiConsumer(this::multiGetCacheResultToMap,
                    new ArrayList<>(keys), resultMap, mGetMaxSize, mGetMaxSize + (mGetMaxSize >> 1));

            // List<String> keyList = new ArrayList<>(keys);
            // int fromIndex = 0, toIndex = mGetMaxSize;
            // while (toIndex < keySize) {
            //     List<String> subKeys = keyList.subList(fromIndex, Math.min(toIndex, keySize));
            //     multiGetCacheResultToMap(subKeys, resultMap);
            //     fromIndex = toIndex;
            //     toIndex += mGetMaxSize;
            //     // 优化：如果最后一次查询的剩余元素个数少于 mGetMinSize 个，则最后两次查询会合并为一次查询
            //     if (keySize - toIndex < mGetMinSize) {
            //         toIndex = keySize;
            //         multiGetCacheResultToMap(keyList.subList(fromIndex, toIndex), resultMap);
            //     }
            // }
        } else {
            multiGetCacheResultToMap(keys, resultMap);
        }
        return resultMap;
    }

    private void multiGetCacheResultToMap(Collection<String> keys, HashMap<String, CacheResult<V>> result) {
        List<byte[]> values = cache.opsForValue().multiGet(keys);
        Iterator<String> keyIterator = keys.iterator();
        Iterator<byte[]> valueIterator = values.iterator();
        while (keyIterator.hasNext() && valueIterator.hasNext()) {
            byte[] value = valueIterator.next();
            CacheResult<V> cacheResult;
            if (value == null) {
                cacheResult = CacheResults.fail();
            } else {
                try {
                    cacheResult = warp(converter.decode(value));
                } catch (ConvertException e) {
                    log.error("Redis value decode fail", e);
                    cacheResult = CacheResults.fail();
                }
            }
            result.put(keyIterator.next(), cacheResult);
        }
    }

    @Override
    public void put(String key, V value, Long expire) {
        cache.opsForValue().set(key, converter.encode(CacheHolders.init(value, expire)), Duration.ofMillis(expire));
    }

    @Override
    public void putAll(Map<String, ? extends V> values, Long expire) {
        HashMap<String, byte[]> convertMap = new HashMap<>(values.size());
        StringRedisSerializer keySerializer = (StringRedisSerializer) cache.getKeySerializer();
        values.forEach((k, v) -> convertMap.put(k, converter.encode(CacheHolders.init(v, expire))));
        cache.executePipelined((RedisCallback<Object>) conn -> {
            convertMap.forEach((k, v) -> conn.pSetEx(keySerializer.serialize(k), expire, v));
            return null;
        });
    }

    @Override
    public boolean putIfNotExist(String key, Long expire) {
        byte[] randomKey = (Thread.currentThread().getName() + System.currentTimeMillis()).getBytes();
        Boolean success = cache.opsForValue().setIfAbsent(key, randomKey, Duration.ofMillis(expire));
        return success == null ? false : success;
    }

    @Override
    public Set<String> putMultiIfNotExist(Set<String> keys, Long expire) {
        // 此处应该是value吧？
        byte[] randomKey = (Thread.currentThread().getName() + System.currentTimeMillis()).getBytes();
        StringRedisSerializer keySerializer = (StringRedisSerializer) cache.getKeySerializer();
        LinkedList<String> keyArray = new LinkedList<>(keys);
        List<Object> doResult = cache.executePipelined((RedisCallback<Set<String>>) connection -> {
            keyArray.forEach(key -> connection.pSetEx(keySerializer.serialize(key), expire, randomKey));
            return null;
        });
        HashSet<String> result = new HashSet<>();
        for (int i = 0; i < keyArray.size(); i++) {
            if ((Boolean) doResult.get(i)) {
                result.add(keyArray.get(i));
            }
        }
        return result;
    }

    @Override
    public boolean remove(String key) {
        Boolean delete = cache.delete(key);
        return delete == null ? false : delete;
    }

    @Override
    public void removeAll(Set<String> keys) {
        cache.delete(keys);
    }

}

