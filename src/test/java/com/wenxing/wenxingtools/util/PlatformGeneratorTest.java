package com.wenxing.wenxingtools.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台生成枚举顺序（AGENTS §3.7 T1 常驻化）。
 * <p>核心不变式：分批执行的游标与一次性驱动必须遍历同一条格子序列，否则「一次生成」与
 * 「跨 tick 生成」会得到不同的平台与不同的掉落。
 */
class PlatformGeneratorTest {

    private static final int ANCHOR_Y = 64;

    @Test
    void plannedCellsMatchSegmentSizes() {
        int aboveHeight = 320 - ANCHOR_Y - 1;
        long fill = 48L * 48L * 2L;
        long ring = 54L * 54L * 2L - fill;
        long above = 54L * 54L * aboveHeight;
        PlatformGenerator.Cursor cursor = new PlatformGenerator.Cursor(0, 0, ANCHOR_Y, 320);
        assertEquals(fill + ring + above, cursor.plannedCells());
    }

    @Test
    void cursorVisitsExpectedSegments() {
        PlatformGenerator.Cursor cursor = new PlatformGenerator.Cursor(0, 0, ANCHOR_Y, 320);
        int fill = 0;
        int ring = 0;
        int above = 0;
        boolean fillInBounds = true;
        boolean ringOutsidePlatform = true;
        int firstX = Integer.MIN_VALUE;
        int firstY = Integer.MIN_VALUE;
        int firstZ = Integer.MIN_VALUE;
        int aboveMinY = Integer.MAX_VALUE;
        int aboveMaxY = Integer.MIN_VALUE;
        while (cursor.advance()) {
            int x = cursor.x();
            int y = cursor.y();
            int z = cursor.z();
            switch (cursor.phase()) {
                case FILL -> {
                    if (fill == 0) {
                        firstX = x;
                        firstY = y;
                        firstZ = z;
                    }
                    fill++;
                    if (!PlatformGeometry.inPlatform(x, z, 0, 0) || (y != ANCHOR_Y - 1 && y != ANCHOR_Y)) {
                        fillInBounds = false;
                    }
                }
                case RING -> {
                    ring++;
                    if (PlatformGeometry.inPlatform(x, z, 0, 0)) {
                        ringOutsidePlatform = false;
                    }
                }
                case ABOVE -> {
                    above++;
                    aboveMinY = Math.min(aboveMinY, y);
                    aboveMaxY = Math.max(aboveMaxY, y);
                }
                default -> {
                }
            }
        }
        assertEquals(-16, firstX);
        assertEquals(ANCHOR_Y - 1, firstY);
        assertEquals(-16, firstZ);
        assertEquals(48 * 48 * 2, fill);
        assertEquals(54 * 54 * 2 - 48 * 48 * 2, ring);
        assertEquals(54 * 54 * (320 - ANCHOR_Y - 1), above);
        assertTrue(fillInBounds);
        assertTrue(ringOutsidePlatform);
        assertEquals(ANCHOR_Y + 1, aboveMinY);
        assertEquals(319, aboveMaxY);
        assertEquals(PlatformGenerator.Phase.DONE, cursor.phase());
    }

    @Test
    void oneShotDriverFollowsCursorOrder() {
        int maxHeight = 8;
        List<int[]> fromCursor = new ArrayList<>();
        PlatformGenerator.Cursor cursor = new PlatformGenerator.Cursor(0, 0, ANCHOR_Y, maxHeight);
        while (cursor.advance()) {
            fromCursor.add(new int[] {cursor.x(), cursor.y(), cursor.z()});
        }

        List<int[]> fromRun = new ArrayList<>();
        PlatformGenerator.run(0, 0, ANCHOR_Y, maxHeight,
                (x, y, z) -> fromRun.add(new int[] {x, y, z}),
                (x, y, z) -> fromRun.add(new int[] {x, y, z}),
                (x, y, z) -> false);

        assertEquals(fromCursor.size(), fromRun.size(), "一次性驱动与游标必须遍历同样多的格子");
        for (int i = 0; i < fromCursor.size(); i++) {
            int[] c = fromCursor.get(i);
            int[] r = fromRun.get(i);
            assertEquals(c[0], r[0], "第 " + i + " 格的 x 不一致");
            assertEquals(c[1], r[1], "第 " + i + " 格的 y 不一致");
            assertEquals(c[2], r[2], "第 " + i + " 格的 z 不一致");
        }
    }

    @Test
    void oneShotDriverSkipsClearForAir() {
        // 空气不参与清除结算：probe 返回 true 时 clear 不应被调用
        PlatformGenerator.Counts counts = PlatformGenerator.run(0, 0, ANCHOR_Y, 8,
                (x, y, z) -> {
                },
                (x, y, z) -> {
                    throw new AssertionError("空气不应触发清除");
                },
                (x, y, z) -> true);
        assertEquals(48 * 48 * 2, counts.filled());
        assertEquals(0, counts.ringCleared());
        assertEquals(0, counts.aboveCleared());
    }

    @Test
    void shallowBuildHeightHasNoAboveSegment() {
        PlatformGenerator.Cursor cursor = new PlatformGenerator.Cursor(0, 0, 300, 301);
        int above = 0;
        while (cursor.advance()) {
            if (cursor.phase() == PlatformGenerator.Phase.ABOVE) {
                above++;
            }
        }
        assertEquals(0, above);
    }

    @Test
    void playerEmbeddedWithinPlatformLayers() {
        assertTrue(PlatformGenerator.playerEmbedded(ANCHOR_Y - 1, ANCHOR_Y));
        assertTrue(PlatformGenerator.playerEmbedded(ANCHOR_Y, ANCHOR_Y));
        assertFalse(PlatformGenerator.playerEmbedded(ANCHOR_Y - 2, ANCHOR_Y));
        assertFalse(PlatformGenerator.playerEmbedded(ANCHOR_Y + 1, ANCHOR_Y));
    }

    @Test
    void advanceStopsAtDoneAndStaysThere() {
        PlatformGenerator.Cursor cursor = new PlatformGenerator.Cursor(0, 0, ANCHOR_Y, 66);
        int guard = 0;
        while (cursor.advance()) {
            guard++;
            if (guard > 200_000) {
                break;
            }
        }
        assertFalse(cursor.advance());
        assertEquals(PlatformGenerator.Phase.DONE, cursor.phase());
    }
}
