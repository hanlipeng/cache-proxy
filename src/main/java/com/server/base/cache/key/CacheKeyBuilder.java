package com.server.base.cache.key;

import com.server.base.cache.annotation.CacheConst;
import com.server.generic.util.JsonUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author: caoyanan
 * @time: 2020/6/4 5:23 下午
 */
public class CacheKeyBuilder {


    /**
     * 用户指定的keyword
     */
    private String keyword;

    /**
     * 从缓存值对象中获取key值的方法
     */
    private String keyValueMethod;


    /**
     * 缓存的class
     */
    private Class<?> cacheClass;


    public CacheKeyBuilder() {
    }

    public CacheKeyBuilder(String keyword, String keyValueMethod, Class<?> cacheClass) {
        this.keyword = keyword;
        this.keyValueMethod = keyValueMethod;
        this.cacheClass = cacheClass;
    }

    /**
     * 构建缓存key
     *
     * @return
     */
    public List<String> buildKeys(Object param) {
        //1. 生成前缀
        String prefix = keyword;
        if (Objects.equals(CacheConst.UNDEFINED_STRING, prefix)) {
            prefix = buildPrefix(cacheClass, keyValueMethod);
        }

        //2. key前缀部分和key参数值部分的连接
        List<String> cacheKeys;
        if (param instanceof Collection) {
            String finalPrefix = prefix;
            cacheKeys = ((Collection<?>) param).stream()
                    .map(oneParam -> buildPrefixAndValue(finalPrefix, oneParam))
                    .collect(Collectors.toList());
        } else {
            cacheKeys = Collections.singletonList(buildPrefixAndValue(prefix,
                    Collections.singletonList(param)));
        }
        return cacheKeys;
    }

    /**
     * 从更新参数中提取到key的值
     * 比如 Student(id:2,name:jack)，提取方法是getId。那么需要从这个参数对象中提取出来2返回
     *
     * @param args          更新方法aop拦截的参数对象
     * @param extractMethod 提取key值的方法
     * @return
     */
    public Object extractKeyValue(Object[] args, String extractMethod) {
        if (args == null || args.length < 1) {
            throw new RuntimeException("no param");
        }
        Object extractArg = args[0];

        try {
            Method method = cacheClass.getMethod(extractMethod);
            method.setAccessible(true);

            if (!(extractArg instanceof Collection)) {
                return method.invoke(extractArg);
            }
            return ((Collection<?>) extractArg).stream()
                    .map(oneParam -> {
                        try {
                            return method.invoke(oneParam);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }


    /**
     * key前缀的构建，形如：public class com.server.base.cache.entity.CacheResult<T>.getId
     */
    public static String buildPrefix(Class<?> cacheClass, String keyValueMethod) {
        return String.format("%s.%s", cacheClass.toGenericString(), keyValueMethod);
    }

    /**
     * key的前缀和值的构建
     *
     * @param prefix
     * @param paramValue
     * @return
     */
    public static String buildPrefixAndValue(String prefix, Object paramValue) {
        // 原有代码：return String.format("%s%s", prefix, JsonUtils.toJsonNode(paramValue));  ，"+"操作比"format"操作性能高20倍
        return prefix + JsonUtils.toJsonNode(paramValue);
    }

}
