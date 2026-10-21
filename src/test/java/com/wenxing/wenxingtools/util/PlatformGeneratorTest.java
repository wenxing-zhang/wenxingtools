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
 * <p>破坏范围与平台同宽：只有 填充 → 上方清除 两段，上方固定 {@link PlatformGeometry#ABOVE_LAYERS} 层。
 */
class PlatformGeneratorTest {

    private static final int ANCHOR_Y = 64;
    private static final int MAX_BUILD_HEIGHT = 320;

    @Test
    void plannedCellsMatchSegmentSizes() {
        for (ChunkScope scope : ChunkScope.values()) {
            int side = scope.blocks();
            long fill = (long) side * side * PlatformGeometry.LAYERS;
            long above = (long) side * side * PlatformGeometry.ABOVE_LAYERS;
            PlatformGenerator.Cursor cursor =
                    new PlatformGenerator.Cursor(scope, 0, 0, ANCHOR_Y, MAX_BUILD_HEIGHT);
            assertEquals(fill + above, cursor.plannedCells(), scope + " 的计划格子数");
            assertEquals(side, cursor.side(), scope + " 的游标边长");
        }
    }

    @Test
    void fifteenScopePlansAboutOneMillionCells() {
        long side = ChunkScope.FIFTEEN.blocks();
        long expected = side * side * (PlatformGeometry.LAYERS + PlatformGeometry.ABOVE_LAYERS);
        assertEquals(240L * 240L * 18L, expected);
        PlatformGenerator.Cursor cursor =
                new PlatformGenerator.Cursor(ChunkScope.FIFTEEN, 0, 0, ANCHOR_Y, MAX_BUILD_HEIGHT);
        assertEquals(expected, cursor.plannedCells());
    }

    @Test
    void cursorVisitsExpectedSegments() {
        PlatformGenerator.Cursor cursor =
                new PlatformGenerator.Cursor(ChunkScope.THREE, 0, 0, ANCHOR_Y, MAX_BUILD_HEIGHT);
        int fill = 0;
        int above = 0;
        boolean fillInBounds = true;
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
                    if (x < PlatformGeometry.minX(0, ChunkScope.THREE)
                            || x > PlatformGeometry.maxX(0, ChunkScope.THREE)
                            || z < PlatformGeometry.minZ(0, ChunkScope.THREE)
                            || z > PlatformGeometry.maxZ(0, ChunkScope.THREE)
                            || (y != ANCHOR_Y - 1 && y != ANCHOR_Y)) {
                        fillInBounds = false;
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
        assertEquals(48 * 48 * PlatformGeometry.ABOVE_LAYERS, above);
        assertTrue(fillInBounds);
        assertEquals(ANCHOR_Y + 1, aboveMinY, "上方清除必须从平台顶面之上开始");
        assertEquals(ANCHOR_Y + PlatformGeometry.ABOVE_LAYERS, aboveMaxY, "上方清除固定 16 层");
        assertEquals(PlatformGenerator.Phase.DONE, cursor.phase());
    }

    @Test
    void aboveClearingNeverCrossesIntoFillLayers() {
        PlatformGenerator.Cursor cursor =
                new PlatformGenerator.Cursor(ChunkScope.NINE, 0, 0, ANCHOR_Y, MAX_BUILD_HEIGHT);
        while (cursor.advance()) {
            if (cursor.phase() == PlatformGenerator.Phase.ABOVE) {
                assertTrue(cursor.y() > ANCHOR_Y, "上方清除不得落进平台实心层");
            }
        }
    }

    @Test
    void oneShotDriverFollowsCursorOrder() {
        int maxHeight = 8;
        for (ChunkScope scope : ChunkScope.values()) {
            List<int[]> fromCursor = new ArrayList<>();
            PlatformGenerator.Cursor cursor = new PlatformGenerator.Cursor(scope, 0, 0, ANCHOR_Y, maxHeight);
            while (cursor.advance()) {
                fromCursor.add(new int[] {cursor.x(), cursor.y(), cursor.z()});
            }

            List<int[]> fromRun = new ArrayList<>();
            PlatformGenerator.run(scope, 0, 0, ANCHOR_Y, maxHeight,
                    (x, y, z) -> fromRun.add(new int[] {x, y, z}),
                    (x, y, z) -> fromRun.add(new int[] {x, y, z}),
                    (x, y, z) -> false);

            assertEquals(fromCursor.size(), fromRun.size(), scope + "：一次性驱动与游标必须遍历同样多的格子");
            for (int i = 0; i < fromCursor.size(); i++) {
                int[] c = fromCursor.get(i);
                int[] r = fromRun.get(i);
                assertEquals(c[0], r[0], "第 " + i + " 格的 x 不一致");
                assertEquals(c[1], r[1], "第 " + i + " 格的 y 不一致");
                assertEquals(c[2], r[2], "第 " + i + " 格的 z 不一致");
            }
        }
    }

    @Test
    void oneShotDriverSkipsClearForAir() {
        // 空气不参与清除结算：probe 返回 true 时 clear 不应被调用
        PlatformGenerator.Counts counts = PlatformGenerator.run(ChunkScope.THREE, 0, 0, ANCHOR_Y, 8,
                (x, y, z) -> {
                },
                (x, y, z) -> {
                    throw new AssertionError("空气不应触发清除");
                },
                (x, y, z) -> true);
        assertEquals(48 * 48 * 2, counts.filled());
        assertEquals(0, counts.aboveCleared());
    }

    @Test
    void shallowBuildHeightHasNoAboveSegment() {
        PlatformGenerator.Cursor cursor =
                new PlatformGenerator.Cursor(ChunkScope.THREE, 0, 0, 300, 301);
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
        PlatformGenerator.Cursor cursor =
                new PlatformGenerator.Cursor(ChunkScope.THREE, 0, 0, ANCHOR_Y, 66);
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
