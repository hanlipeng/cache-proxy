package com.server.base.cache.annotation;


import java.lang.annotation.*;

/**
 * @author: caoyanan
 * @time: 2020/6/4 5:01 下午
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
public @interface CacheRemove {

    /**
     * 缓存key的前缀
     * @return
     */
    String keyWord() default CacheConst.UNDEFINED_STRING;

    /**
     * 缓存@Cached设置的keyValueMethod,在生成前缀的时使用
     * @return
     */
    String cachedKeyValueMethod();

    /**
     * 缓存@Cached设置的类类型，生成前缀时使用
     * @return
     */
    Class<?> cachedClass();

    /**
     * 移除缓存方法参数类中获取缓存key值的方法，比如获取getId的方法
     * @return
     */
    String removeKeyValueMethod();
}
