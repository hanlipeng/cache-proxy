package com.server.base.cache.container;

import com.server.base.cache.entity.CacheHolder;
import com.server.base.cache.entity.CacheResult;
import com.server.base.cache.util.CacheResults;

import java.util.Map;
import java.util.Set;

/**
 * @author hanlipeng
 * @date 2019-07-15
 */
public interface Cache<V> {

    /**
     * 从缓存中获取
     *
     * @param key key
     * @return 值
     */
    CacheResult<V> get(String key);

    /**
     * 批量获取
     *
     * @param keys keys
     * @return 结果
     */
    Map<String, CacheResult<V>> getAll(Set<String> keys);

    /**
     * 存储
     *
     * @param key key
     * @param value value
     * @param expire 过期时间
     */
    void put(String key, V value, Long expire);

    /**
     * 批量存放
     *
     * @param values 键值对
     * @param expire 过期时间
     */
    void putAll(Map<String, ? extends V> values, Long expire);

    /**
     * 如果不存在键则存储值
     *
     * @param key key
     * @param expire 过期时间
     * @return 是否成功
     */
    boolean putIfNotExist(String key, Long expire);

    /**
     * 如果不存在键则存储值
     *
     * @param keys key
     * @param expire 过期时间
     * @return 每一个put成功的key
     */
    Set<String> putMultiIfNotExist(Set<String> keys, Long expire);


    /**
     * 删除值
     *
     * @param key 键
     * @return 结果
     */
    boolean remove(String key);

    /**
     * 批量删除
     *
     * @param keys 键s
     */
    void removeAll(Set<String> keys);

    default CacheResult<V> warp(CacheHolder<V> holder) {
        return CacheResults.success(holder.getData(), holder.getExpireAt());
    }
}
