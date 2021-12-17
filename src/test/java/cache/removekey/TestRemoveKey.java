package cache.removekey;

import com.server.base.cache.key.CacheKeyBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * @author: caoyanan
 * @time: 2020/6/5 4:39 下午
 */
public class TestRemoveKey {

    @Test
    public void testExtractKeyValue() {
        CacheKeyBuilder cacheKeyBuilder = new CacheKeyBuilder("", "", Student.class);
        List<Student> params = LongStream.range(0, 3)
                .mapToObj(it -> Student.builder()
                        .id(it).name("name" + it).build())
                .collect(Collectors.toList());
        Object extractKeyValueResult = cacheKeyBuilder
                .extractKeyValue(new Object[]{params}, "getId");
        Assertions.assertEquals("[0, 1, 2]", extractKeyValueResult.toString());
        Assertions.assertEquals("[name0, name1, name2]", cacheKeyBuilder
                .extractKeyValue(new Object[]{params}, "getName").toString());
    }
}
