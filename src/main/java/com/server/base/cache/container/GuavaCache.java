package com.server.base.cache.container;

import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.server.base.cache.converter.ValueConverter;
import com.server.base.cache.entity.CacheResult;
import com.server.base.cache.exception.ConvertException;
import com.server.base.cache.util.CacheHolders;
import com.server.base.cache.util.CacheResults;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author hanlipeng
 * @date 2019-07-16
 */
@Slf4j
public class GuavaCache<V> implements Cache<V> {

    private final com.google.common.cache.Cache<String, byte[]> cache;

    private final ValueConverter<V> converter;
    private boolean isClose = false;

    private final ExpireListener listener;

    public GuavaCache(ValueConverter<V> converter, int maximumSize) {
        cache = CacheBuilder.newBuilder().maximumSize(maximumSize).build();

        this.converter = converter;
        listener = new ExpireListener();
        Thread thread = new Thread(listener);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public CacheResult<V> get(String key) {
        byte[] value = cache.getIfPresent(key);
        if (value == null) {
            return CacheResults.fail();
        }
        try {
            return warp(converter.decode(value));
        } catch (ConvertException convertException) {
            return CacheResults.fail();
        }
    }

    @Override
    public Map<String, CacheResult<V>> getAll(Set<String> keys) {
        ImmutableMap<String, byte[]> allPresent = cache.getAllPresent(keys);
        HashMap<String, CacheResult<V>> result = new HashMap<>(allPresent.size());
        keys.forEach((k -> {
            byte[] o = allPresent.get(k);
            if (o == null) {
                result.put(k, CacheResults.fail());
                return;
            }
            CacheResult<V> success;
            try {
                success = warp(converter.decode(o));
            } catch (ConvertException convertException) {
                success = CacheResults.fail();
            }
            result.put(k, success);
        }));
        return result;
    }

    @Override
    public void put(String key, V value, Long expire) {
        byte[] encode = converter.encode(CacheHolders.init(value, expire));
        cache.put(key, encode);
        addListener(key, expire);
    }

    @Override
    public void putAll(Map<String, ? extends V> values, Long expire) {
        HashMap<String, byte[]> temp = new HashMap<>(values.size());
        values.forEach((k, v) -> temp.put(k, converter.encode(CacheHolders.init(v, expire))));
        cache.putAll(temp);
        addListener(values.keySet(), expire);
    }

    @Override
    public boolean putIfNotExist(String key, Long expire) {
        byte[] randomKey = (Thread.currentThread().getName() + System.currentTimeMillis()).getBytes();
        try {
            Object bytes = cache.get(key, () -> randomKey);
            if (bytes == randomKey) {
                addListener(key, expire);
                return true;
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    @Override
    public Set<String> putMultiIfNotExist(Set<String> keys, Long expire) {
        return keys.stream().filter(key -> putIfNotExist(key, expire)).collect(Collectors.toSet());
    }

    @Override
    public boolean remove(String key) {
        cache.invalidate(key);
        return true;
    }

    @Override
    public void removeAll(Set<String> keys) {
        cache.invalidateAll(keys);
    }

    public void close() {
        cache.invalidateAll();
        isClose = true;
    }

    private void addListener(String key, long expire) {
        listener.addListener(System.currentTimeMillis() + expire, key);
    }

    private void addListener(Set<String> key, long expire) {
        listener.addAllListener(System.currentTimeMillis() + expire, key);
    }

    private class ExpireListener implements Runnable {

        private TreeMap<Long, Set<String>> outOfTimeMap = new TreeMap<>(Long::compare);

        @Override
        public void run() {
            while (!isClose) {
                try {
                    TimeUnit.MILLISECONDS.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (outOfTimeMap.isEmpty()) {
                    continue;
                }
                long now = System.currentTimeMillis();
                while (true) {
                    Entry<Long, Set<String>> entry = outOfTimeMap.floorEntry(now);
                    if (entry == null) {
                        break;
                    }
                    log.debug("delete key {} now {} expireTime {}", entry.getValue(), now, entry.getKey());
                    removeAll(entry.getValue());
                    outOfTimeMap.remove(entry.getKey());
                }
            }
        }

        public synchronized void addListener(Long outTime, String key) {
            if (outTime < System.currentTimeMillis()) {
                remove(key);
            }
            outOfTimeMap.compute(outTime, (k, v) -> {
                if (v == null) {
                    v = new HashSet<>();
                }
                v.add(key);
                return v;
            });
        }

        public synchronized void addAllListener(Long outTime, Set<String> key) {
            if (outTime < System.currentTimeMillis()) {
                removeAll(key);
            }
            outOfTimeMap.compute(outTime, (k, v) -> {
                if (v == null) {
                    v = new HashSet<>();
                }
                v.addAll(key);
                return v;
            });
        }
    }
}
