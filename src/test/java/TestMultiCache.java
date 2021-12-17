import com.server.base.cache.container.DataCache;
import com.server.base.cache.container.GuavaCache;
import com.server.base.cache.container.LinkCache;
import com.server.base.cache.converter.KryoValueConverter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author hanlipeng
 * @date 2019-07-23
 */
public class TestMultiCache {

    @Test
    public void testSingleParam() {
        DataCache<String> caches = initCaches();
        AtomicInteger loadTimes = new AtomicInteger(0);
        Collection<String> test1 = caches.getCacheOrLoad("test",
                t -> {
                    loadTimes.incrementAndGet();
                    return t + "cache";
                },
                t -> t + " key",
                true,
                100L,
                10L
        );
        Collection<String> test2 = caches.getCacheOrLoad("test",
                t -> {
                    loadTimes.incrementAndGet();
                    return t + "cache";
                },
                t -> t + " key",
                true,
                100L,
                10L
        );
        Assertions.assertEquals(test1, test2);
        Assertions.assertEquals(1, loadTimes.get());
    }

    @Test
    public void testMultiParam() {
        DataCache<String> caches = initCaches();
        AtomicInteger loadTimes = new AtomicInteger(0);
        Collection<String> test1 = caches.getCacheOrLoadList(
                Arrays.asList("test1", "test2"),
                t -> {
                    loadTimes.incrementAndGet();
                    return t.stream().map(m -> "cache" + m).collect(Collectors.toList());
                },
                t -> t + "Key",
                t -> ((String) t).replace("cache", ""),
                true,
                100L,
                10L
        );
        System.out.println(test1);
    }

    @Test
    public void testMultiParamNull() {
        DataCache<String> caches = initCaches();
        AtomicInteger loadTimes = new AtomicInteger(0);
        Collection<String> test1 = caches.getCacheOrLoadList(Arrays.asList(null, "test2"),
                t -> {
                    loadTimes.incrementAndGet();
                    return t.stream().map(m -> "cache" + m).collect(Collectors.toList());
                },
                t -> t + "Key",
                t -> {
                    String param = ((String) t).replace("cache", "");
                    if (param.equals("null")) {
                        return null;
                    }
                    return param;
                },
                true,
                100L,
                10L
        );
        System.out.println(test1);
    }


    private DataCache<String> initCaches() {
        GuavaCache<Collection<String>> cache1 = new GuavaCache<>(new KryoValueConverter<>(), 100);
        GuavaCache<Collection<String>> cache2 = new GuavaCache<>(new KryoValueConverter<>(), 100);
        List<GuavaCache<Collection<String>>> cacheList = Arrays.asList(cache1, cache2);
        return new LinkCache<>(cacheList);
    }
}
