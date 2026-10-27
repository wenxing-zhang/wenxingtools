package com.wenxing.wenxingtools.util;

/**
 * 多合一实体的计数预算：总量靠计数承载，不靠条目数。
 * 纯计算，不依赖 MC 运行时（可用隔离探针直接验证）。
 */
public final class EntryBudget {
    private EntryBudget() {}

    /** 饱和到 int 上限：超过 21 亿的部分按既定「溢出蒸发」丢弃，不静默回绕成负数。 */
    public static int saturate(long count) {
        if (count <= 0L) {
            return 0;
        }
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    /** 同款两条计数相加后饱和。 */
    public static int merge(int current, int incoming) {
        return saturate((long) current + (long) incoming);
    }
}
