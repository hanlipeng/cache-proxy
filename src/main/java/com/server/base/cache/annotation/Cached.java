package com.server.base.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * @author hanlipeng
 * @date 2019-07-16
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
public @interface Cached {

    /**
     * 缓存key的前缀：本参数值将会作为前缀，拼接到缓存key的前面
     * 如果没有手动指定，则默认值见 {@link com.server.base.cache.key.CacheKeyBuilder#buildPrefix(java.lang.Class, java.lang.String)}
     */
    String keyWord() default CacheConst.UNDEFINED_STRING;

    /**
     * 从相应值中获取请求参数值的方法名，如：{@code getId}
     */
    String keyValueMethod();

    /**
     * 缓存过期的时长，会与unit()配合得到最终的缓存有效时间（默认单位：秒）
     */
    long expireTime() default CacheConst.UNDEFINED_LONG;

    /**
     * 缓存过期的时间单位
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 要缓存的 java Class 类型，如 {@code User.class}
     */
    Class entityClass();

    /**
     * 是否缓存null值
     */
    boolean cacheNull() default false;

    /**
     * null值的过期时间，单位：毫秒
     */
    long nullValueExpireTime() default CacheConst.UNDEFINED_LONG;

}
