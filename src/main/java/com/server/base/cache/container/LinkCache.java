package com.server.base.cache.container;

import com.server.base.cache.entity.CacheResult;
import com.server.base.cache.exception.CacheException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * @author hanlipeng
 * @date 2021/3/13
 */
@Slf4j
public class LinkCache<V> implements DataCache<V> {

    private CacheNode<V> first;

    private CacheNode<V> last;

    public LinkCache(List<? extends Cache<Collection<V>>> caches) {
        if (caches.isEmpty()) {
            first = last = new CacheNode<>(new NoCache<>());
        } else {
            for (Cache<Collection<V>> cache : caches) {
                if (first == null) {
                    last = first = new CacheNode<>(cache);
                } else {
                    last.next = new CacheNode<>(cache);
                    last = last.next;
                }

            }
        }

    }

    @Override
    public <P> List<V> getCacheOrLoadList(Collection<P> param, Function<Collection<P>, Object> loader, Function<P, String> keyBuilder, Function<Object, P> methodThatGetParamFromData, boolean cacheNull, Long expire, Long nullExpire) {
        return first.getCacheOrLoadList(param, loader, keyBuilder, methodThatGetParamFromData, cacheNull, expire, nullExpire);
    }

    @Override
    public <P> Collection<V> getCacheOrLoad(P param, Function<P, V> loader, Function<P, String> keyBuilder, boolean cacheNull, Long expire, Long nullExpire) {
        return first.getCacheOrLoad(param, loader, keyBuilder, cacheNull, expire, nullExpire);
    }

    @Override
    public void removeCache(Set<String> keys) {
        first.removeCache(keys);
    }


    private static class CacheNode<V> implements DataCache<V> {

        private static final long LOCK_TIME = 10000L;

        private CacheNode<V> next;

        private final Cache<Collection<V>> cache;

        private CacheNode(Cache<Collection<V>> cache) {
            this.cache = cache;
        }

        private boolean hasNext() {
            return next != null;
        }

        @Override
        public <P> List<V> getCacheOrLoadList(Collection<P> param, Function<Collection<P>, Object> loader,
                                              Function<P, String> keyBuilder, Function<Object, P> methodThatGetParamFromData, boolean cacheNull,
                                              Long expire, Long nullExpire) {


            List<ParamPack<P>> paramPacks = param.stream()
                    .map(p -> ParamPack.buildParamPack(p, keyBuilder)).collect(Collectors.toList());


            return getCacheOrLoadList(new ParamCombination<>(paramPacks, param.getClass()), loader, methodThatGetParamFromData, cacheNull, expire, nullExpire);
        }


        // ??????????????????
        private <P> List<V> getCacheOrLoadList(ParamCombination<P> paramCombination, Function<Collection<P>, Object> loader, Function<Object, P> methodThatGetParamFromData, boolean cacheNull, Long expire, Long nullExpire) {
            List<V> result = new ArrayList<>();

            // ???????????????????????????????????????????????????????????????
            int count = 0;
            while (!paramCombination.isClear()) {
                Set<String> cacheKeys = paramCombination.getCacheKeys();
                // ??????keys???????????????????????????value????????????????????????????????????????????????result???
                Map<String, CacheResult<Collection<V>>> cacheData = cache.getAll(cacheKeys);
                cacheData
                        .forEach((key, cacheResult) -> {
                            if (cacheResult.isSuccess()) {
                                paramCombination.removeByCacheKeys(key);
                                result.addAll(Optional.of(cacheResult).map(CacheResult::getData).orElse(Collections.emptyList()));
                            }
                        });

                // ???????????????????????????????????????????????????????????????????????????
                if (paramCombination.isClear()) {
                    return result;
                }

                Set<String> needUnlockKey = new HashSet<>();
                try {
                    // ??????????????????????????????key?????????????????????????????????lockKey????????????????????????????????????????????????
                    Set<String> successLock = cache.putMultiIfNotExist(paramCombination.getLockKeys(), LOCK_TIME);
                    needUnlockKey.addAll(successLock);
                    if (!successLock.isEmpty()) {
                        // ?????????key???????????????????????????????????????????????????key????????????????????? ??????????????????????????????
                        Map<String, CacheResult<Collection<V>>> hasLoadedData = cache.getAll(paramCombination.getCacheKeys());
                        hasLoadedData.forEach((k, v) -> {
                            if (v.isSuccess()) {
                                result.addAll(Optional.of(v).map(CacheResult::getData).orElse(Collections.emptyList()));
                                paramCombination.removeByCacheKeys(k);
                            }
                        });
                        // ???????????????key ??? ???????????????????????????key ?????????????????? "??????????????????????????????????????????keys"
                        successLock.retainAll(paramCombination.getLockKeys());
                        // ??????successLock??????????????????????????????????????????????????????????????????????????????continue????????????????????????"???????????????????????????????????????"
                        if (successLock.isEmpty()) {
                            continue;
                        }

                        // ?????????????????????????????????????????????????????????????????????????????????????????????????????????
                        // ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                        List<V> loadResult = loadListData(paramCombination, loader, methodThatGetParamFromData, cacheNull, expire, nullExpire, successLock);
                        result.addAll(loadResult);

                        // ????????????????????????????????????
                        saveListData(paramCombination, methodThatGetParamFromData, cacheNull, expire, nullExpire, successLock, loadResult);

                        // ???param???????????????????????????param
                        successLock.forEach(paramCombination::removeByLockKeys);
                    }
                } finally {
                    if (!needUnlockKey.isEmpty()) {
                        cache.removeAll(needUnlockKey);
                    }
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (count++ > 5) {
                    log.warn("count of load data from cache has bean more than 5 times , total times is :{}", count);
                }
            }

            return result;

        }

        private <P> List<V> loadListData(ParamCombination<P> paramCombination, Function<Collection<P>, Object> loader, Function<Object, P> methodThatGetParamFromData, boolean cacheNull, Long expire, Long nullExpire, Set<String> successLock) {
            List<ParamPack<P>> paramPacks = paramCombination.getByLockKey(successLock);
            List<V> loadResult;
            // ????????????????????????????????????????????????????????????????????????????????????????????????
            if (hasNext()) {
                loadResult = next.getCacheOrLoadList(new ParamCombination<>(paramPacks, paramCombination.getParamClass()), loader, methodThatGetParamFromData, cacheNull, expire, nullExpire);
                // ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            } else {
                List<P> params = paramPacks.stream()
                        .map(ParamPack::getParam)
                        .collect(Collectors.toList());
                loadResult = new ArrayList<>((Collection<V>) loader.apply(params));
            }
            return loadResult;
        }

        private <P> void saveListData(ParamCombination<P> paramCombination, Function<Object, P> methodThatGetParamFromData, boolean cacheNull, Long expire, Long nullExpire, Set<String> successLock, List<V> loadResult) {
            Map<String, List<V>> dataGroupByCacheKey = loadResult.stream().collect(Collectors.groupingBy(data -> paramCombination.getCacheKeyByParam(methodThatGetParamFromData.apply(data))));
            if (cacheNull) {
                Map<String, List<V>> nullKeyList = successLock.stream()
                        .map(paramCombination::getCacheKeyByLockKey)
                        .filter(keys -> !dataGroupByCacheKey.containsKey(keys))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(Function.identity(), k -> Collections.emptyList(), (l, r) -> l));
                cache.putAll(nullKeyList, nullExpire);
            }
            cache.putAll(dataGroupByCacheKey, expire);
        }


        @Override
        public <P> Collection<V> getCacheOrLoad(P param, Function<P, V> loader, Function<P, String> keyBuilder, boolean cacheNull, Long expire, Long nullExpire) {
            ParamPack<P> paramPack = ParamPack.buildParamPack(param, keyBuilder);

            return getCacheOrLoad(paramPack, loader, cacheNull, expire, nullExpire);
        }

        @Override
        public void removeCache(Set<String> keys) {
            cache.removeAll(keys);
            if (hasNext()) {
                next.removeCache(keys);
            }
        }

        private <P> Collection<V> getCacheOrLoad(ParamPack<P> paramPack, Function<P, V> loader, boolean cacheNull, Long expire, Long nullExpire) {
            Collection<V> result = null;

            String cacheKey = paramPack.getCacheKey();
            String lockKey = paramPack.getLockKey();
            while (result == null) {
                CacheResult<Collection<V>> cacheData = cache.get(cacheKey);
                if (cacheData.isSuccess()) {
                    result = cacheData.getData();
                    break;
                }
                boolean locked = cache.putIfNotExist(lockKey, LOCK_TIME);
                if (locked) {
                    try {
                        cacheData = cache.get(cacheKey);
                        if (cacheData.isSuccess()) {
                            return cacheData.getData();
                        }
                        if (hasNext()) {
                            result = next.getCacheOrLoad(paramPack, loader, cacheNull, expire, nullExpire);
                        } else {
                            result = loadData(paramPack, loader);
                        }
                        savaData(cacheNull, expire, nullExpire, result, cacheKey);
                    } finally {
                        cache.remove(lockKey);
                    }
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            return result;
        }

        private void savaData(boolean cacheNull, Long expire, Long nullExpire, Collection<V> result, String cacheKey) {
            if (result.isEmpty()) {
                if (cacheNull) {
                    cache.put(cacheKey, result, nullExpire);
                }
            } else {
                cache.put(cacheKey, result, expire);
            }
        }

        private <P> Collection<V> loadData(ParamPack<P> paramPack, Function<P, V> loader) {
            Collection<V> result;
            V data = loader.apply(paramPack.getParam());
            if (data == null) {
                result = Collections.emptyList();
            } else {
                if (data instanceof Collection) {
                    result = (Collection<V>) data;
                } else {
                    result = Collections.singletonList(data);
                }
            }
            return result;
        }

    }

    @Data
    private static class ParamPack<P> {

        private P param;

        private String cacheKey;

        private String lockKey;


        private ParamPack() {

        }

        public static <P> ParamPack<P> buildParamPack(P param, Function<P, String> keyBuilder) {
            ParamPack<P> pack = new ParamPack<>();
            pack.param = param;
            pack.cacheKey = keyBuilder.apply(param);
            pack.lockKey = buildLockKey(pack.cacheKey);
            return pack;
        }

        private static String buildLockKey(String key) {
            return "$" + key + "$lock";
        }
    }

    private static class ParamCombination<P> {

        private final Set<ParamPack<P>> paramPacks;

        private final Map<String, ParamPack<P>> cacheKeyMap;

        private final Map<String, ParamPack<P>> lockKeyMap;

        private final Map<P, ParamPack<P>> paramMap;

        private final Class<? extends Collection> paramClass;

        private final Collector<P, ?, ? extends Collection<P>> paramCollectors;

        private ParamCombination(List<ParamPack<P>> paramPacks, Class<? extends Collection> paramClass) {
            cacheKeyMap = paramPacks.stream().collect(Collectors.toMap(ParamPack::getCacheKey, Function.identity(), (l, r) -> l));
            lockKeyMap = paramPacks.stream().collect(Collectors.toMap(ParamPack::getLockKey, Function.identity(), (l, r) -> l));
            paramMap = paramPacks.stream().collect(Collectors.toMap(ParamPack::getParam, Function.identity(), (l, r) -> l));
            //
            this.paramPacks = new HashSet<>(cacheKeyMap.values());
            this.paramClass = paramClass;
            if (List.class.isAssignableFrom(paramClass)) {
                this.paramCollectors = Collectors.toList();
            } else if (Set.class.isAssignableFrom(paramClass)) {
                this.paramCollectors = Collectors.toSet();
            } else {
                throw new CacheException("not support param type" + paramClass);
            }
        }

        public Set<String> getLockKeys() {
            return lockKeyMap.keySet();
        }

        public Set<String> getCacheKeys() {
            return cacheKeyMap.keySet();
        }

        public void removeByCacheKeys(String key) {
            ParamPack<P> paramPack = cacheKeyMap.remove(key);
            lockKeyMap.remove(paramPack.getLockKey());
            paramPacks.remove(paramPack);
        }

        public boolean isClear() {
            return paramPacks.isEmpty();
        }

        public List<ParamPack<P>> getByLockKey(Set<String> successLock) {
            return successLock.stream()
                    .map(lockKeyMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        public Class<? extends Collection> getParamClass() {
            return paramClass;
        }

        public String getCacheKeyByLockKey(String lockKey) {
            return lockKeyMap.get(lockKey).getCacheKey();
        }

        public String getCacheKeyByParam(P param) {
            return paramMap.get(param).getCacheKey();
        }

        public void removeByLockKeys(String lockKey) {
            ParamPack<P> paramPack = lockKeyMap.remove(lockKey);
            cacheKeyMap.remove(paramPack.getCacheKey());
            paramPacks.remove(paramPack);
        }
    }
}
