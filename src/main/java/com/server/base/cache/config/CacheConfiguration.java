package com.server.base.cache.config;

import java.util.concurrent.TimeUnit;
import lombok.Data;

/**
 * @author hanlipeng
 * @date 2019-07-17
 */
@Data
public class CacheConfiguration {

    private CacheConfig local;

    private CacheConfig remote;

    {
        local = new CacheConfig();
        local.setTimeUnit(TimeUnit.SECONDS);
        local.setGlobalExpireTime(60);
        remote = new CacheConfig();
        remote.setGlobalExpireTime(60);
        remote.setTimeUnit(TimeUnit.MINUTES);
    }


}
