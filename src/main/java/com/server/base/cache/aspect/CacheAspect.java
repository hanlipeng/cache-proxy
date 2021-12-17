package com.server.base.cache.aspect;

import com.server.base.cache.annotation.CacheConst;
import com.server.base.cache.annotation.CacheRemove;
import com.server.base.cache.annotation.Cached;
import com.server.base.cache.container.DataCache;
import com.server.base.cache.key.CacheKeyBuilder;
import com.server.generic.util.JsonUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * @author hanlipeng
 * @date 2019-07-17
 */
@Aspect
@Slf4j
public class CacheAspect {

    private final DataCache<Object> cache;

    public CacheAspect(DataCache<Object> cache) {
        this.cache = cache;
    }

    @Pointcut("@annotation(com.server.base.cache.annotation.Cached)")
    public void cachePointCut() {

    }

    @Pointcut("@annotation(com.server.base.cache.annotation.CacheRemove)")
    public void cacheRemovePointCut() {

    }

    @Around("cacheRemovePointCut()")
    public Object aroundCacheRemove(ProceedingJoinPoint point) {
        Object[] args = point.getArgs();
        try {
            Object proceed = point.proceed(args);
            removeCache(point);
            return proceed;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    private void removeCache(ProceedingJoinPoint point) {
        //1. 获取注解
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        CacheRemove cacheRemove = method.getAnnotation(CacheRemove.class);

        //2. 构建key
        //2.1 构建缓存keyBuilder
        CacheKeyBuilder cacheKeyBuilder = new CacheKeyBuilder(cacheRemove.keyWord(),
                cacheRemove.cachedKeyValueMethod(), cacheRemove.cachedClass());
        //2.2 从更新类中提取出key值部分
        Object[] args = point.getArgs();
        Object extractKeyValueParam = cacheKeyBuilder
                .extractKeyValue(args, cacheRemove.removeKeyValueMethod());
        //2.3 生成redis的缓存key
        List<String> redisKeys = cacheKeyBuilder.buildKeys(extractKeyValueParam);

        //3. 缓存的删除
        cache.removeCache(new HashSet<>(redisKeys));
    }

    @Around("cachePointCut()")
    public Object around(ProceedingJoinPoint point) {
        long start = System.currentTimeMillis();
        // 找到使用了注解的方法签名，并构建要缓存数据的描述信息
        MethodSignature signature = (MethodSignature) point.getSignature();
        CacheInfo cacheInfo = CacheInfo.build(signature.getMethod());

        Object[] args = point.getArgs();
        Object result = null;
        // 如果连接点的方法只有一个集合类型的入参
        if (args.length == 1 && args[0] instanceof Collection) {
            // 定义执行切入点方法的函数，调用{@code java.util.function.Function.apply}方法时将会返回切入点方法的执行结果
            Function<Collection<Object>, Object> loader = params -> {
                try {
                    return point.proceed(new Object[]{params});
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            };
            // 定义缓存key的构建函数，该函数将会拼接 cacheInfo.keyPrefix + param 作为返回的key
            Function<Object, String> keyBuilder = param -> CacheKeyBuilder.buildPrefixAndValue(cacheInfo.getKeyPrefix(), param);
            // 定义从缓存的结果值中获取对应请求参数值的函数（methodThatGetParamFromData）
            Function<Object, Object> methodThatGetMethodParamFromMethodResult = obj -> {
                try {
                    return cacheInfo.getMethodThatGetMethodParamFromMethodResult().invoke(obj);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            };
            result = cache.getCacheOrLoadList(
                    (Collection<Object>) args[0],
                    loader,
                    keyBuilder,
                    methodThatGetMethodParamFromMethodResult,
                    cacheInfo.isCacheNull(),
                    cacheInfo.getExpireTime(),
                    cacheInfo.getNullValueExpireTime()
            );
        // 如果连接点的方法入参为其他情况
        } else {
            Function<Object[], Object> loader = params -> {
                try {
                    // 此处与上面调用的api略有不同
                    return point.proceed(params);
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            };
            Function<Object[], String> keyBuilder = params -> cacheInfo.getKeyPrefix() + JsonUtils.getStringJsonUtils().toJson(params);

            // 从缓存中读取或调用切入点方法，得到返回值
            Collection<Object> tmpResult = cache
                    .getCacheOrLoad(args, loader, keyBuilder, cacheInfo.isCacheNull(), cacheInfo.getExpireTime(),
                            cacheInfo.getNullValueExpireTime());
            Class returnType = cacheInfo.getReturnType();
            // 如果在@Cached注解中声明的返回值不是一个集合，并且返回的缓存是一个非空集合，则取集合中的第一个元素
            if (!Collection.class.isAssignableFrom(returnType)) {
                if (tmpResult != null && !tmpResult.isEmpty()) {
                    result = tmpResult.toArray()[0];
                }
            } else {
                result = tmpResult;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("CacheAspect get or init cache cost time {}", System.currentTimeMillis() - start);
        }
        return result;
    }


    /**
     * 缓存相关信息
     */
    @Data
    private static class CacheInfo {

        private Method methodThatGetMethodParamFromMethodResult;

        private String keyPrefix;

        private long expireTime;

        private long localExpireTime;

        private long nullValueExpireTime;

        private Class returnType;

        private boolean cacheNull;

        static CacheInfo build(Method targetMethod) {

            CacheInfo cacheInfo = new CacheInfo();
            Cached annotation = targetMethod.getAnnotation(Cached.class);

            String keyValueMethodName = annotation.keyValueMethod();
            Class cacheClass = annotation.entityClass();
            try {
                // 根据要缓存的java类和key-value映射方法，得到 从方法结果值中获取方法参数的Method
                cacheInfo.methodThatGetMethodParamFromMethodResult = cacheClass.getMethod(keyValueMethodName);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }

            String keyWord = annotation.keyWord();
            if (Objects.equals(CacheConst.UNDEFINED_STRING, keyWord)) {
                // 如果@Cached注解没有指定keyWord参数值，则构建一个由 Class.genericString和keyValueMethodName 组成的缓存key描述符
                keyWord = CacheKeyBuilder.buildPrefix(cacheClass, keyValueMethodName);
            }
            cacheInfo.keyPrefix = keyWord;

            // 提取@Cached缓存中配置的过期时间
            cacheInfo.expireTime = annotation.unit().toMillis(annotation.expireTime());

            cacheInfo.cacheNull = annotation.cacheNull();

            cacheInfo.nullValueExpireTime = annotation.nullValueExpireTime();

            // 被处理方法的返回值类型
            cacheInfo.returnType = targetMethod.getReturnType();

            return cacheInfo;
        }
    }
}
