package com.server.base.cache.container;

import com.server.base.cache.entity.CacheResult;
import com.server.base.cache.exception.CacheException;
import com.server.base.cache.util.CacheResults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * @author hanlipeng
 * @date 2019-07-22
 */
@Slf4j
public class MultiCache<CACHE_TYPE> {

    private static final long LOCK_TIME = 10000L;

    private static final String LOCK_KEY_TEMPLATE = "$%s$lock";

    private final List<? extends Cache<Collection<CACHE_TYPE>>> caches;

    private final int cacheSize;

    public MultiCache(List<? extends Cache<Collection<CACHE_TYPE>>> caches) {
        this.caches = caches;
        checkCache(caches);
        cacheSize = caches.size();
    }

    private void checkCache(List<? extends Cache<Collection<CACHE_TYPE>>> caches) {
        if (CollectionUtils.isEmpty(caches)) {
            throw new CacheException("empty cache List: can not build MultiCache without Cache");
        }
    }

    private String buildLockKey(String key) {
        return String.format(LOCK_KEY_TEMPLATE, key);
    }


    public <P> Collection<CACHE_TYPE> getCacheOrLoad(P param, Function<P, CACHE_TYPE> loader,
        Function<P, String> keyBuilder,
        boolean cacheNull,
        Long expire, Long nullExpire) {
        int index = 0;
        CacheResult<Collection<CACHE_TYPE>> result = CacheResults.fail();
        //build key
        String key = keyBuilder.apply(param);
        //build lock key
        String lockKey = buildLockKey(key);
        try {
            while (index >= 0) {
                Cache<Collection<CACHE_TYPE>> cache = caches.get(index);
                //4.loadSuccess
                if (result.isSuccess()) {
                    putValue(cache, key, result.getData(), cacheNull, expire, nullExpire);
                    cache.remove(buildLockKey(key));
                    index--;
                    continue;
                }
                //1.get
                result = cache.get(key);
                if (result.isSuccess()) {
                    index--;
                    continue;
                }
                //2.lock
                boolean locked = cache.putIfNotExist(lockKey, LOCK_TIME);
                //3.load
                if (locked) {
                    log.trace("single lock init, cache index:{}, cache size:{}", index, cacheSize);
                    result = cache.get(key);
                    if (result.isSuccess()) {
                        index--;
                        cache.remove(lockKey);
                        continue;
                    }
                    if (index < cacheSize - 1) {
                        log.trace("single lock inner cache");
                        index++;
                    } else {
                        log.trace("single lock load data");
                        CACHE_TYPE data = loader.apply(param);
                        if (data == null) {
                            result = CacheResults.success(Collections.emptyList());
                        } else {
                            if (data instanceof Collection) {
                                result = (CacheResult<Collection<CACHE_TYPE>>) CacheResults.success(data);
                            } else {
                                result = CacheResults.success(Collections.singletonList(data));
                            }
                        }
                    }
                    continue;
                }
                log.trace("single lock fail,loop");
                //sleep
                TimeUnit.MILLISECONDS.sleep(10);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return result.getData();
    }

    public <P> List<CACHE_TYPE> getCacheOrLoad(Collection<P> param, Function<Collection<P>, Object> loader,
        Function<P, String> keyBuilder, Function<Object, P> keyMethod, boolean cacheNull,
        Long expire, Long nullExpire) {

        Collector<P, ?, ? extends Collection<P>> paramCollectors;
        if (param instanceof List) {
            paramCollectors = Collectors.toList();
        } else if (param instanceof Set) {
            paramCollectors = Collectors.toSet();
        } else {
            throw new CacheException("not support param type" + param.getClass());
        }

        int index = 0;
        Map<String, Collection<CACHE_TYPE>> result = new HashMap<>(param.size());
        //build key
        Map<String, P> keyParamMap = buildKeyMap(param, keyBuilder);
        Set<String> keys = keyParamMap.keySet();
        Map<String, String> keyLockKeyMap = keys.stream()
            .collect(Collectors.toMap(Function.identity(), this::buildLockKey));
        Map<Integer, Set<String>> indexKeyMap = new HashMap<>(cacheSize);
        indexKeyMap.put(0, new HashSet<>(keys));
        HashMap<Integer, Set<String>> indexLockedKeyMap = new HashMap<>(cacheSize);
        try {
            while (index >= 0) {
                Cache<Collection<CACHE_TYPE>> cache = caches.get(index);
                Set<String> presentKeys = indexKeyMap.get(index);
                //loadSuccess 这段方法用于递归后将value存入cache中
                //6.save
                Set<String> lockedKeys = indexLockedKeyMap.getOrDefault(index, Collections.emptySet());
                if (!lockedKeys.isEmpty()) {
                    Map<String, Collection<CACHE_TYPE>> existResult = getAll(result, lockedKeys);
                    putAllValue(cache, lockedKeys, existResult, cacheNull, expire, nullExpire);
                    presentKeys.removeAll(lockedKeys);
                    cache.removeAll(getAllExistValue(keyLockKeyMap, keys, Collectors.toSet()));
                    if (presentKeys.isEmpty()) {
                        indexKeyMap.remove(index--);
                        continue;
                    }
                }
                //1.get
                Map<String, CacheResult<Collection<CACHE_TYPE>>> tmpResult = cache.getAll(presentKeys);
                //filter
                Set<String> successKey = filterSuccessKey(tmpResult);
                if (!successKey.isEmpty()) {
                    Map<String, Collection<CACHE_TYPE>> successResult = getAll(tmpResult, successKey,
                        CacheResult::getData);
                    //result 中只存成功获取的数据,未命中数据不存在result中
                    mergeMap(result, successResult);
                    presentKeys.removeAll(successKey);
                    if (presentKeys.isEmpty()) {
                        indexKeyMap.remove(index--);
                        continue;
                    }
                }
                //lock
                lockedKeys = multiLock(cache, presentKeys, keyLockKeyMap::get);


                if (!lockedKeys.isEmpty()) {
                    //lockGet
                    tmpResult = cache.getAll(presentKeys);
                    successKey = filterSuccessKey(tmpResult);
                    if (!successKey.isEmpty()) {
                        Map<String, Collection<CACHE_TYPE>> successResult = getAll(tmpResult, successKey,
                            CacheResult::getData);
                        //result 中只存成功获取的数据,未命中数据不存在result中
                        mergeMap(result, successResult);
                        presentKeys.removeAll(successKey);
                        lockedKeys.removeAll(successKey);
                        if (lockedKeys.isEmpty()) {
                            cache.removeAll(getAllExistValue(keyLockKeyMap, lockedKeys, Collectors.toSet()));
                            if (presentKeys.isEmpty()) {
                                indexKeyMap.remove(index--);
                            }
                            continue;
                        }
                    }
                    //load
                    indexLockedKeyMap.put(index, lockedKeys);
                    //锁key之后如果不是最终层缓存则递归下一个缓存,否则加载数据
                    if (index < cacheSize - 1) {
                        indexKeyMap.put(++index, new HashSet<>(lockedKeys));
                    } else {
                        Collection<P> lockedParam = getAllExistValue(keyParamMap, lockedKeys, paramCollectors);
                        Collection<CACHE_TYPE> dataCollection = (Collection<CACHE_TYPE>) loader.apply(lockedParam);
                        Map<String, List<CACHE_TYPE>> collect = dataCollection.stream()
                            .collect(Collectors.groupingBy(obj -> keyBuilder.apply(keyMethod.apply(obj))));
                        Map<String, List<CACHE_TYPE>> values = getAll(collect, lockedKeys);
                        mergeMap(result, values);
                    }
                    continue;
                }
                //sleep
                TimeUnit.MILLISECONDS.sleep(10);

            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //抛异常之后释放所有的锁
            indexLockedKeyMap.forEach((i, lockedKey) -> caches.get(i)
                .removeAll(getAllExistValue(keyLockKeyMap, lockedKey, Collectors.toSet())));
        }
        List<CACHE_TYPE> collect = result.values().stream().flatMap(Collection::stream).filter(Objects::nonNull)
            .collect(Collectors.toList());
        return collect;
    }

    private <P> Map<String, P> buildKeyMap(Collection<P> param, Function<P, String> keyBuilder) {
        HashMap<String, P> result = new HashMap<>(param.size());
        for (P p : param) {
            result.put(keyBuilder.apply(p), p);
        }
        return result;
    }

    private void putValue(Cache<Collection<CACHE_TYPE>> cache, String key, Collection<CACHE_TYPE> value,
        boolean cacheNull, long expire,
        long nullExpire) {
        if (value.isEmpty()) {
            if (cacheNull) {
                expire = nullExpire;
            } else {
                return;
            }
        }
        cache.put(key, value, expire);
    }

    private void putAllValue(Cache<Collection<CACHE_TYPE>> cache, Set<String> keys,
        Map<String, Collection<CACHE_TYPE>> values,
        boolean cacheNull,
        long expire,
        long nullExpire) {
        if (cacheNull) {
            Map<String, Collection<CACHE_TYPE>> nullValues = keys.stream()
                .filter(key -> Objects.isNull(values.get(key)))
                .collect(Collectors.toMap(Function.identity(),
                    t -> Collections.emptyList()));
            cache.putAll(nullValues, nullExpire);
        }
        cache.putAll(values, expire);
    }

    private <K, V> Map<K, V> mergeMap(Map<K, V> oldValue,
        Map<K, ? extends V> newValue) {
        newValue.forEach((k, v) -> oldValue.compute(k, (k1, v1) -> newValue.get(k1)));
        return oldValue;
    }

    private Set<String> filterSuccessKey(Map<String, CacheResult<Collection<CACHE_TYPE>>> values) {
        HashSet<String> result = new HashSet<>();
        values.forEach((k, v) -> {
            if (v.isSuccess()) {
                result.add(k);
            }
        });
        return result;
    }

    private Set<String> multiLock(Cache<Collection<CACHE_TYPE>> cache, Set<String> keys,
        Function<String, String> lockKeyBuilder) {
//        return cache.putMultiIfNotExist(keys, LOCK_TIME, lockKeyBuilder);
        return null;
    }

    private <K, V, R> R getAllExistValue(Map<K, V> map, Set<K> keys, Collector<V, ?, R> collector) {
        Objects.requireNonNull(map);
        Objects.requireNonNull(keys);
        return keys.stream().filter(map::containsKey).map(map::get).collect(collector);
    }

    private <K, T, V> Map<K, V> getAll(Map<K, T> map, Set<K> keys, Function<T, V> mapping) {
        Objects.requireNonNull(map);
        Objects.requireNonNull(keys);
        Objects.requireNonNull(mapping);
        HashMap<K, V> result = new HashMap<>(keys.size());
        for (K key : keys) {
            result.put(key, mapping.apply(map.get(key)));
        }
        return result;
    }

    private <K, V> Map<K, V> getAll(Map<K, V> map, Set<K> keys) {
        Objects.requireNonNull(map);
        Objects.requireNonNull(keys);
        return getAll(map, keys, Function.identity());
    }

    public void remove(Collection<String> keys) {
        for (Cache<Collection<CACHE_TYPE>> cach : caches) {
            cach.removeAll(new HashSet<>(keys));
        }
    }

}
