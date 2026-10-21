package com.wenxing.wenxingtools.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 区块档位与平台几何边界：按档位对齐的整数运算（AGENTS §3.7 T1 常驻化）。 */
class PlatformGeometryTest {

    @Test
    void scopeDerivedValues() {
        assertEquals(3, ChunkScope.THREE.chunks());
        assertEquals(1, ChunkScope.THREE.radiusChunks());
        assertEquals(48, ChunkScope.THREE.blocks());
        assertEquals("3乘3", ChunkScope.THREE.label());

        assertEquals(9, ChunkScope.NINE.chunks());
        assertEquals(4, ChunkScope.NINE.radiusChunks());
        assertEquals(144, ChunkScope.NINE.blocks());
        assertEquals("9乘9", ChunkScope.NINE.label());

        assertEquals(15, ChunkScope.FIFTEEN.chunks());
        assertEquals(7, ChunkScope.FIFTEEN.radiusChunks());
        assertEquals(240, ChunkScope.FIFTEEN.blocks());
        assertEquals("15乘15", ChunkScope.FIFTEEN.label());
    }

    @Test
    void scopeCyclesThroughThreeSteps() {
        assertEquals(ChunkScope.NINE, ChunkScope.THREE.next());
        assertEquals(ChunkScope.FIFTEEN, ChunkScope.NINE.next());
        assertEquals(ChunkScope.THREE, ChunkScope.FIFTEEN.next());
    }

    @Test
    void scopeRoundTripsThroughStoredChunkCount() {
        for (ChunkScope scope : ChunkScope.values()) {
            assertEquals(scope, ChunkScope.of(scope.chunks()), scope + " 必须由存储的区块数还原");
        }
        assertEquals(ChunkScope.THREE, ChunkScope.of(0), "未知值（含旧存档缺属性）落到默认档");
        assertEquals(ChunkScope.THREE, ChunkScope.of(7), "未知值落到默认档");
    }

    @Test
    void platformBoundsAlignedToChunks() {
        assertEquals(-16, PlatformGeometry.minX(0, ChunkScope.THREE));
        assertEquals(31, PlatformGeometry.maxX(0, ChunkScope.THREE));
        assertEquals(-16, PlatformGeometry.minZ(0, ChunkScope.THREE));
        assertEquals(31, PlatformGeometry.maxZ(0, ChunkScope.THREE));

        assertEquals(-64, PlatformGeometry.minX(0, ChunkScope.NINE));
        assertEquals(79, PlatformGeometry.maxX(0, ChunkScope.NINE));
        assertEquals(-64, PlatformGeometry.minZ(0, ChunkScope.NINE));
        assertEquals(79, PlatformGeometry.maxZ(0, ChunkScope.NINE));

        assertEquals(-112, PlatformGeometry.minX(0, ChunkScope.FIFTEEN));
        assertEquals(127, PlatformGeometry.maxX(0, ChunkScope.FIFTEEN));
        assertEquals(-112, PlatformGeometry.minZ(0, ChunkScope.FIFTEEN));
        assertEquals(127, PlatformGeometry.maxZ(0, ChunkScope.FIFTEEN));
    }

    @Test
    void platformWidthAlwaysMatchesScopeBlocks() {
        for (ChunkScope scope : ChunkScope.values()) {
            int width = PlatformGeometry.maxX(0, scope) - PlatformGeometry.minX(0, scope) + 1;
            assertEquals(scope.blocks(), width, scope + " 的水平宽度必须等于档位方块数");
        }
    }

    @Test
    void aboveHeightCappedAtSixteenLayers() {
        assertEquals(PlatformGeometry.ABOVE_LAYERS, PlatformGeometry.aboveHeight(64, 320));
    }

    @Test
    void aboveHeightTruncatedByBuildHeight() {
        assertEquals(15, PlatformGeometry.aboveHeight(304, 320));
        assertEquals(0, PlatformGeometry.aboveHeight(319, 320));
        assertEquals(0, PlatformGeometry.aboveHeight(320, 320));
        assertEquals(0, PlatformGeometry.aboveHeight(400, 320));
    }

    @Test
    void layersStackDownwardFromAnchor() {
        assertEquals(64, PlatformGeometry.topY(64));
        assertEquals(63, PlatformGeometry.bottomY(64));
    }
}
