package com.wenxing.wenxingtools.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 击杀掉落入世层窗口判定（AGENTS §3.7 T1 常驻化）。
 * <p>窗口是「推断归属」：放宽会误放大方块破坏掉落与物流输出，收窄会漏掉延后一刻的生成。
 */
class DropWindowUtilTest {

    @Test
    void windowCoversSameTickAndNextTick() {
        assertTrue(DropWindowUtil.inWindow(100, 100, 0));
        assertTrue(DropWindowUtil.inWindow(101, 100, 0));
    }

    @Test
    void windowRejectsLaterAndPastTicks() {
        assertFalse(DropWindowUtil.inWindow(102, 100, 0));
        assertFalse(DropWindowUtil.inWindow(99, 100, 0));
    }

    @Test
    void radiusBoundaryInclusive() {
        double edge = DropWindowUtil.RADIUS * DropWindowUtil.RADIUS;
        assertTrue(DropWindowUtil.inWindow(100, 100, edge));
        assertFalse(DropWindowUtil.inWindow(100, 100, edge + 0.0001));
    }

    @Test
    void nanDistanceRejected() {
        assertFalse(DropWindowUtil.inWindow(100, 100, Double.NaN));
    }

    @Test
    void tuningConstants() {
        assertEquals(1, DropWindowUtil.WINDOW_TICKS);
        assertEquals(4.0D, DropWindowUtil.RADIUS);
    }
}
