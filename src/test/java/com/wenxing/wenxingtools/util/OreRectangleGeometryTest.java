package com.wenxing.wenxingtools.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 矿石矩形几何契约：99 格偏移唯一、层序连续、每层恰好覆盖一个 3×3 面（AGENTS §3.7 T1 常驻化）。 */
class OreRectangleGeometryTest {

    @Test
    void dimensionsAreWiredTogether() {
        assertEquals(3, OreRectangleGeometry.SIDE);
        assertEquals(11, OreRectangleGeometry.ORE_KINDS);
        assertEquals(9, OreRectangleGeometry.LAYER_CELLS);
        assertEquals(5, OreRectangleGeometry.LAYER_HALF);
        assertEquals(99, OreRectangleGeometry.cellCount());
    }

    @Test
    void offsetsAreAllDistinct() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < OreRectangleGeometry.cellCount(); i++) {
            assertTrue(seen.add(OreRectangleGeometry.xOffset(i) + "," + OreRectangleGeometry.yOffset(i)
                    + "," + OreRectangleGeometry.zOffset(i)), "索引 " + i + " 的偏移与前序重复");
        }
        assertEquals(OreRectangleGeometry.cellCount(), seen.size());
    }

    @Test
    void offsetsStayInsideDeclaredBox() {
        for (int i = 0; i < OreRectangleGeometry.cellCount(); i++) {
            assertTrue(Math.abs(OreRectangleGeometry.xOffset(i)) <= 1, "x 越界 @ " + i);
            assertTrue(Math.abs(OreRectangleGeometry.yOffset(i)) <= 1, "y 越界 @ " + i);
            assertTrue(Math.abs(OreRectangleGeometry.zOffset(i)) <= OreRectangleGeometry.LAYER_HALF, "z 越界 @ " + i);
        }
    }

    @Test
    void eachLayerCoversOneFull3By3Face() {
        for (int layer = 0; layer < OreRectangleGeometry.ORE_KINDS; layer++) {
            Set<String> layerCells = new HashSet<>();
            for (int cell = 0; cell < OreRectangleGeometry.LAYER_CELLS; cell++) {
                int index = layer * OreRectangleGeometry.LAYER_CELLS + cell;
                assertEquals(layer, OreRectangleGeometry.oreIndexOf(index), "层号错位 @ " + index);
                assertEquals(layer - OreRectangleGeometry.LAYER_HALF, OreRectangleGeometry.zOffset(index));
                layerCells.add(OreRectangleGeometry.xOffset(index) + "," + OreRectangleGeometry.yOffset(index));
            }
            assertEquals(9, layerCells.size(), "第 " + layer + " 层未覆盖完整 3×3 面");
            assertTrue(layerCells.contains("-1,-1") && layerCells.contains("1,1"), "第 " + layer + " 层缺角");
        }
    }

    @Test
    void layerStartsAtMinusHalfAndEndsAtPlusHalf() {
        assertEquals(-OreRectangleGeometry.LAYER_HALF, OreRectangleGeometry.zOffset(0));
        assertEquals(OreRectangleGeometry.LAYER_HALF,
                OreRectangleGeometry.zOffset(OreRectangleGeometry.cellCount() - 1));
        assertEquals(0, OreRectangleGeometry.oreIndexOf(0));
        assertEquals(OreRectangleGeometry.ORE_KINDS - 1,
                OreRectangleGeometry.oreIndexOf(OreRectangleGeometry.cellCount() - 1));
    }

    @Test
    void buildRangeIsHalfOpenOnTop() {
        int minY = -64;
        int maxY = 320;
        assertFalse(OreRectangleGeometry.inBuildRange(minY - 1, minY, maxY), "低于最低建造高度必须裁剪");
        assertTrue(OreRectangleGeometry.inBuildRange(minY, minY, maxY), "最低建造高度本身可写");
        assertTrue(OreRectangleGeometry.inBuildRange(maxY - 1, minY, maxY), "建造上限下一格可写");
        assertFalse(OreRectangleGeometry.inBuildRange(maxY, minY, maxY), "建造上限本身不可写（开区间上界）");
        assertFalse(OreRectangleGeometry.inBuildRange(0, minY, minY), "空区间一律裁剪");
    }

    @Test
    void chunkOfHandlesNegativeCoordinates() {
        assertEquals(-1, OreRectangleGeometry.chunkOf(-1), "-1 格属于区块 -1，不是 0");
        assertEquals(-1, OreRectangleGeometry.chunkOf(-16), "-16 是区块 -1 的首格");
        assertEquals(-2, OreRectangleGeometry.chunkOf(-17));
        assertEquals(0, OreRectangleGeometry.chunkOf(0));
        assertEquals(0, OreRectangleGeometry.chunkOf(15));
        assertEquals(1, OreRectangleGeometry.chunkOf(16));
    }

    @Test
    void offsetsRejectOutOfRangeIndex() {
        assertThrows(IllegalArgumentException.class, () -> OreRectangleGeometry.xOffset(-1));
        assertThrows(IllegalArgumentException.class, () -> OreRectangleGeometry.yOffset(OreRectangleGeometry.cellCount()));
        assertThrows(IllegalArgumentException.class, () -> OreRectangleGeometry.zOffset(OreRectangleGeometry.cellCount()));
        assertThrows(IllegalArgumentException.class, () -> OreRectangleGeometry.oreIndexOf(-1));
    }
}
