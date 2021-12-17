package com.server.base.cache.config;

import java.util.concurrent.TimeUnit;
import lombok.Data;

/**
 * @author hanlipeng
 * @date 2019-07-17
 */
@Data
public class CacheConfig {

    private long globalExpireTime;

    private TimeUnit timeUnit;
}
