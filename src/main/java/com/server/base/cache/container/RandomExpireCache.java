package com.server.base.cache.container;

import com.server.base.cache.entity.CacheResult;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * @author hanlipeng
 * @date 2019-07-16
 */
public class RandomExpireCache<V> implements Cache<V> {

    private final Cache<V> cache;
    private final Random random;

    public RandomExpireCache(Cache<V> cache) {
        this.cache = cache;
        random = new Random();
    }

    @Override
    public CacheResult<V> get(String key) {
        return cache.get(key);
    }

    @Override
    public Map<String, CacheResult<V>> getAll(Set<String> keys) {
        return cache.getAll(keys);
    }

    @Override
    public void put(String key, V value, Long expire) {
        cache.put(key, value, randomExpire(expire));
    }

    @Override
    public void putAll(Map<String, ? extends V> values, Long expire) {
        cache.putAll(values, randomExpire(expire));
    }

    @Override
    public boolean putIfNotExist(String key, Long expire) {
        return cache.putIfNotExist(key, randomExpire(expire));
    }

    @Override
    public Set<String> putMultiIfNotExist(Set<String> keys, Long expire) {
        return cache.putMultiIfNotExist(keys, randomExpire(expire));
    }

    @Override
    public boolean remove(String key) {
        return cache.remove(key);
    }

    @Override
    public void removeAll(Set<String> keys) {
        cache.removeAll(keys);
    }

    private Long randomExpire(Long time) {
        long result = (long) (time * (1 + random.nextDouble()));
        return result;
    }
}
