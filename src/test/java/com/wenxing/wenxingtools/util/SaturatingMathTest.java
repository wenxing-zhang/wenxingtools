package com.wenxing.wenxingtools.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 饱和乘法边界（AGENTS §3.7 T1 常驻化）。
 * <p>附魔等级可来自 NBT，裸乘溢出成负值会让倍率、经验与掉落数量静默归零，故逐条锁住边界。
 */
class SaturatingMathTest {

    @Test
    void zeroOrNegativeOperandYieldsZero() {
        assertEquals(0L, SaturatingMath.multiply(0, 123));
        assertEquals(0L, SaturatingMath.multiply(123, 0));
        assertEquals(0L, SaturatingMath.multiply(-1, 5));
        assertEquals(0L, SaturatingMath.multiply(5, -1));
    }

    @Test
    void normalProduct() {
        assertEquals(42L, SaturatingMath.multiply(7, 6));
        assertEquals(1_000_000_000_000_000_000L, SaturatingMath.multiply(1_000_000_000L, 1_000_000_000L));
    }

    @Test
    void overflowSaturatesInsteadOfWrapping() {
        assertEquals(Long.MAX_VALUE, SaturatingMath.multiply(Long.MAX_VALUE, 1));
        // 2^62 * 2 = 2^63：落进符号位，裸乘会得到 Long.MIN_VALUE
        assertEquals(Long.MAX_VALUE, SaturatingMath.multiply(1L << 62, 2));
        // 2^62 * 4：超过 2^64，multiplyHigh 非零
        assertEquals(Long.MAX_VALUE, SaturatingMath.multiply(1L << 62, 4));
    }

    @Test
    void extremeLevelFactorNeverGoesNegative() {
        // 模拟「倍率系数 = 等级 × 每级系数」：INT_MAX × 100 仍在 long 范围内，必须如实保留
        long factor = SaturatingMath.multiply(Integer.MAX_VALUE, 100L);
        assertEquals(214_748_364_700L, factor);
        assertTrue(factor > 0, "极端等级下的倍率系数不得为负");
        // 再把该系数乘上另一个极端等级：约 4.6e20 已超 long 上限，必须饱和而不是回绕成负数
        assertEquals(Long.MAX_VALUE, SaturatingMath.multiply(factor, Integer.MAX_VALUE));
        // 经验链的两级相乘同样不得落成负值
        long xp = SaturatingMath.multiply(SaturatingMath.multiply(Integer.MAX_VALUE, factor), 100L);
        assertEquals(Long.MAX_VALUE, xp);
    }
}
