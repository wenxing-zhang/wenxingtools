package com.wenxing.wenxingtools.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
