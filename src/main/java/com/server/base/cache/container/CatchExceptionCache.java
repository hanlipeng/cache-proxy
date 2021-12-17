package com.server.base.cache.container;

import com.server.base.cache.entity.CacheResult;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * @author hanlipeng
 * @date 2019-08-01
 */
@Slf4j
public class CatchExceptionCache<V> implements Cache<V> {

    private Cache<V> defaultCache = new NoCache<>();

    private final String defaultCacheClass;
    private Cache<V> cache;

    public CatchExceptionCache(Cache<V> cache) {
        this.cache = cache;
        defaultCacheClass = defaultCache.getClass().getSimpleName();
    }

    public CatchExceptionCache(Cache<V> cache, Cache<V> defaultCache) {
        this.defaultCache = defaultCache;
        this.cache = cache;
        defaultCacheClass = defaultCache.getClass().getSimpleName();
    }

    @Override
    public CacheResult<V> get(String key) {
        try {
            return cache.get(key);
        } catch (Exception e) {
            logError(e);
            return defaultCache.get(key);
        }
    }

    @Override
    public Map<String, CacheResult<V>> getAll(Set<String> keys) {
        try {
            return cache.getAll(keys);
        } catch (Exception e) {
            logError(e);
            return defaultCache.getAll(keys);
        }
    }

    @Override
    public void put(String key, V value, Long expire) {
        try {
            cache.put(key, value, expire);
        } catch (Exception e) {
            logError(e);
            defaultCache.put(key, value, expire);
        }
    }

    @Override
    public void putAll(Map<String, ? extends V> values, Long expire) {
        try {
            cache.putAll(values, expire);
        } catch (Exception e) {
            logError(e);
            defaultCache.putAll(values, expire);
        }
    }

    @Override
    public boolean putIfNotExist(String key, Long expire) {
        try {
            return cache.putIfNotExist(key, expire);
        } catch (Exception e) {
            logError(e);
            return defaultCache.putIfNotExist(key, expire);
        }
    }

    @Override
    public Set<String> putMultiIfNotExist(Set<String> keys, Long expire) {
        try {
            return cache.putMultiIfNotExist(keys, expire);
        } catch (Exception e) {
            logError(e);
            return defaultCache.putMultiIfNotExist(keys, expire);
        }
    }

    @Override
    public boolean remove(String key) {
        try {
            return cache.remove(key);
        } catch (Exception e) {
            logError(e);
            return defaultCache.remove(key);
        }
    }

    @Override
    public void removeAll(Set<String> keys) {
        try {
            cache.removeAll(keys);
        } catch (Exception e) {
            logError(e);
            defaultCache.removeAll(keys);
        }
    }

    private void logError(Exception e) {
        log.error("Cache Container has error use " + defaultCacheClass + " as default cache  \n", e);
    }
}
