package com.server.base.cache.container;

import com.server.base.cache.entity.CacheResult;
import com.server.base.cache.util.CacheResults;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author hanlipeng
 * @date 2019-07-19
 */
public class NoCache<V> implements Cache<V> {

    @Override
    public CacheResult<V> get(String key) {
        return CacheResults.fail();
    }

    @Override
    public Map<String, CacheResult<V>> getAll(Set<String> keys) {
        return keys.stream().collect(Collectors.toMap(Function.identity(), t -> CacheResults.fail()));
    }

    @Override
    public void put(String key, V value, Long expire) {

    }

    @Override
    public void putAll(Map<String, ? extends V> values, Long expire) {

    }

    @Override
    public boolean putIfNotExist(String key, Long expire) {
        return true;
    }

    @Override
    public Set<String> putMultiIfNotExist(Set<String> keys, Long expire) {
        return keys;
    }

    @Override
    public boolean remove(String key) {
        return true;
    }

    @Override
    public void removeAll(Set<String> keys) {

    }
}
