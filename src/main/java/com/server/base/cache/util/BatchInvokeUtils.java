package com.server.base.cache.util;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * @author Zephyr
 * @date 2021/9/23.
 */
public final class BatchInvokeUtils {

    /**
     * @param biConsumer 实际要执行的操作
     * @param paramList  需要切分调用的参数
     * @param param2     第二个函数需要的参数
     * @param batchSize  每次调用的典型容量
     * @param threshold  分批调用的最小阈值，小于等于此阈值不会触发分批操作
     */
    public static <T, U> void batchInvokeBiConsumer(BiConsumer<List<T>, U> biConsumer, List<T> paramList, U param2, int batchSize, int threshold) {
        if (paramList.isEmpty()) {
            return;
        }
        assert batchSize > 1 && threshold > 1;
        int listSize = paramList.size();

        if (listSize > threshold && threshold > batchSize) {

            int fromIndex = 0, toIndex = batchSize;
            while (toIndex < listSize) {
                List<T> subKeys = paramList.subList(fromIndex, Math.min(toIndex, listSize));
                biConsumer.accept(subKeys, param2);

                fromIndex = toIndex;
                toIndex += batchSize;
                // 优化：如果"最后一次"查询的剩余元素个数少于 batchSize 的一半，则最后两次查询会合并为一次查询
                if (listSize - toIndex < batchSize >> 1) {
                    toIndex = listSize;
                    biConsumer.accept(paramList.subList(fromIndex, toIndex), param2);
                }
            }
        } else {
            biConsumer.accept(paramList, param2);
        }
    }
}
