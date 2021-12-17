package cache.util;

import com.server.base.cache.util.BatchInvokeUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Zephyr
 * @date 2021/9/23.
 */
public class BatchInvokeUtilsTest {

    @Test
    public void batchInvokeBiConsumer() {
        BiConsumer<List<Integer>, Map<Integer, Integer>> biConsumer = (list1, map) -> {
            list1.forEach(e -> map.put(e, e));
        };
        // 测试用例
        List<Integer> eg = Arrays.asList(1, 99, 100, 101, 199, 200, 201);
        int batchSize = 100;
        int threshold = 60;


        for (Integer example : eg) {
            List<Integer> listParam = new ArrayList<>();
            for (int i = 0; i < example; i++) {
                listParam.add(i);
            }
            Map<Integer, Integer> result = new HashMap<>(listParam.size());

            BatchInvokeUtils.batchInvokeBiConsumer(biConsumer, listParam, result, batchSize, threshold);

            assertTrue(isEqualList(listParam, result.keySet()));
        }

        System.out.println("batchInvokeBiConsumer test passed...");
    }

    private static boolean isEqualList(final Collection<?> list1, final Collection<?> list2) {
        if (list1 == list2) {
            return true;
        }
        if (list1 == null || list2 == null || list1.size() != list2.size()) {
            return false;
        }

        final Iterator<?> it1 = list1.iterator();
        final Iterator<?> it2 = list2.iterator();
        Object obj1 = null;
        Object obj2 = null;

        while (it1.hasNext() && it2.hasNext()) {
            obj1 = it1.next();
            obj2 = it2.next();

            if (!(obj1 == null ? obj2 == null : obj1.equals(obj2))) {
                return false;
            }
        }

        return !(it1.hasNext() || it2.hasNext());
    }
}