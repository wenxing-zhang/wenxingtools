package com.wenxing.wenxingtools.util;

/**
 * 非负整数饱和运算。与 Minecraft 运行时无关，便于隔离验证。
 */
public final class SaturatingMath {

    private SaturatingMath() {
    }

    /**
     * 非负饱和乘法：溢出时锁在 {@link Long#MAX_VALUE}，不返回负数。
     * <p>
     * 附魔等级可来自 NBT，上限不受控；裸乘会溢出成负值，让倍率、经验与掉落数量静默归零。
     */
    public static long multiply(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        long product = a * b;
        // multiplyHigh 非零说明超过 2^64；为 0 但符号位落下说明落进 [2^63, 2^64)
        if (Math.multiplyHigh(a, b) != 0L || product < 0L) {
            return Long.MAX_VALUE;
        }
        return product;
    }
}
