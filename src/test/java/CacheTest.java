import com.server.base.cache.container.Cache;
import com.server.base.cache.container.GuavaCache;
import com.server.base.cache.converter.KryoValueConverter;
import com.server.base.cache.entity.CacheResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

/**
 * @author hanlipeng
 * @date 2019-07-16
 */
public class CacheTest {

    Cache<String> cache = new GuavaCache<>(new KryoValueConverter<>(), 100);

    @Test
    public void testPutGet() {
        String in = "test";
        cache.put("test", in, 10000L);
        String out = cache.get("test").getData();
        Assertions.assertEquals(in, out);
    }

    @Test
    public void testOutOfTime() throws InterruptedException {
        String in = "test";
        cache.put("test", in, 1000L);
        String out = cache.get("test").getData();
        Assertions.assertEquals(in, out);
        TimeUnit.SECONDS.sleep(2L);
        CacheResult<String> test = cache.get("test");
        Assertions.assertFalse(test.isSuccess());
    }

    @Test
    public void testMultiOutOfTime() throws InterruptedException {
        String in = "test";
        cache.put("test1", in, 1000L);
        cache.put("test2", in, 3000L);
        TimeUnit.MILLISECONDS.sleep(500L);
        CacheResult<String> out1 = cache.get("test1");
        CacheResult<String> out2 = cache.get("test2");
        Assertions.assertEquals(in, out1.getData());
        Assertions.assertEquals(in, out2.getData());
        TimeUnit.MILLISECONDS.sleep(1500L);
        out1 = cache.get("test1");
        out2 = cache.get("test2");
        Assertions.assertFalse(out1.isSuccess());
        Assertions.assertEquals(in, out2.getData());
        TimeUnit.MILLISECONDS.sleep(2000L);
        out2 = cache.get("test2");
        Assertions.assertFalse(out2.isSuccess());
    }

    @Test
    public void testRandomExpireCache() throws InterruptedException {
//        GuavaCache<K, V> guavaCache = new GuavaCache<>(new KryoValueConverter<Object>(), new JsonKeyEncoder<Long>());
//        Cache<String, Object> cache = new RandomExpireCache<>(guavaCache);
//        for (int i = 0; i < 10; i++) {
//            cache.put(String.valueOf(i), i, 1000L);
//        }
//        TimeUnit.MILLISECONDS.sleep(1000);
//        cache.getAll()
    }


}
