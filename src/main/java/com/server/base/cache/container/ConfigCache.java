package com.server.base.cache.container;

import com.server.base.cache.config.CacheConfig;
import com.server.base.cache.entity.CacheResult;
import java.util.Map;
import java.util.Set;

/**
 * @author hanlipeng
 * @date 2019-07-22
 */
public class ConfigCache<V> implements Cache<V> {

    private CacheConfig cacheConfig;
    private Cache<V> cache;

    public ConfigCache(CacheConfig cacheConfig, Cache<V> cache) {
        this.cacheConfig = cacheConfig;
        this.cache = cache;
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
        if (expire == null || expire < 0L) {
            expire = getGlobalExpireTime();
        }
        cache.put(key, value, expire);
    }

    @Override
    public void putAll(Map<String, ? extends V> values, Long expire) {
        if (expire == null || expire <= 0L) {
            expire = getGlobalExpireTime();
        }
        cache.putAll(values, expire);
    }

    @Override
    public boolean putIfNotExist(String key, Long expire) {
        return cache.putIfNotExist(key, expire);
    }

    @Override
    public Set<String> putMultiIfNotExist(Set<String> keys, Long expire) {
        return cache.putMultiIfNotExist(keys, expire);
    }

    @Override
    public boolean remove(String key) {
        return cache.remove(key);
    }

    @Override
    public void removeAll(Set<String> keys) {
        cache.removeAll(keys);
    }

    private long getGlobalExpireTime() {
        return cacheConfig.getTimeUnit().toMillis(cacheConfig.getGlobalExpireTime());
    }
}
