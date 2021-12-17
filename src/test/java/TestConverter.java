import com.server.base.cache.converter.KryoValueConverter;
import com.server.base.cache.exception.ConvertException;
import com.server.base.cache.util.CacheHolders;
import com.server.base.cache.util.KryoUtil;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;

/**
 * @author hanlipeng
 * @date 2019-07-15
 */
public class TestConverter {

    @Test
    public void test() throws ConvertException {
        KryoValueConverter kryoValueConverter = new KryoValueConverter();
        LinkedList<String> objects = new LinkedList<>();
        objects.add("test");
        objects.add("test");
        objects.add("test");
        objects.add("test");
        byte[] encode = kryoValueConverter.encode(CacheHolders.init(objects));
        Object decode = kryoValueConverter.decode(encode);
        System.out.println(decode.getClass().getCanonicalName());
        System.out.println(decode.toString());

    }

    @Test
    public void testNull() throws ConvertException {
        KryoValueConverter<Object> converter = new KryoValueConverter<>();
        byte[] encode = converter.encode(null);
        Object decode = converter.decode(encode);
        System.out.println(decode);
    }

    @Test
    public void testToString() {
        TestEntity test = new TestEntity();
        test.setTest1("1");
        test.setTest2("2");
        String s = KryoUtil.writeToString(test);
        System.out.println(s);
    }

    @Test
    public void testError() {
        try {
            String str = "AQBUZXN0Q29udmVydGVyJFRlc3RFbnRpdPkBAYIxAYIy";
            TestEntity test = KryoUtil.readFromString(str);
            System.out.println(test);
        } catch (Exception e) {
            System.err.println(e);
        }

    }

    @Data
    private static class TestEntity {

        private String test1;
        private String test2;
        private String test3;
    }

}
