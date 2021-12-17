package com.server.base.cache.container;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * @author hanlipeng
 * @date 2021/3/13
 */
public interface DataCache<CACHE_TYPE> {


    /**
     * @param param      集合类型的方法调用请求参数
     * @param loader     切入点的原有函数方法
     * @param keyBuilder 根据请求参数构建缓存中key值的函数
     * @param methodThatGetParamFromData 从切面方法的返回值中获取对应请求参数值的函数方法
     * @param cacheNull  是否缓存请求参数对应结果值为null的请求参数
     */
    <P> List<CACHE_TYPE> getCacheOrLoadList(Collection<P> param, Function<Collection<P>, Object> loader,
                                            Function<P, String> keyBuilder, Function<Object, P> methodThatGetParamFromData, boolean cacheNull,
                                            Long expire, Long nullExpire);

    /**
     * @param param      调用方法的原始请求参数
     * @param loader     切入点的原有函数方法
     * @param keyBuilder 根据请求参数构建缓存中key值的函数
     * @param cacheNull  是否缓存请求参数对应结果值为null的请求参数
     */
    <P> Collection<CACHE_TYPE> getCacheOrLoad(P param, Function<P, CACHE_TYPE> loader,
                                              Function<P, String> keyBuilder,
                                              boolean cacheNull,
                                              Long expire, Long nullExpire);

    void removeCache(Set<String> keys);

}
