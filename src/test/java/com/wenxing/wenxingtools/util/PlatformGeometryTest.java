package com.wenxing.wenxingtools.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 平台几何边界：3×3 区块对齐与向外扩 3 格的整数运算（AGENTS §3.7 T1 常驻化）。 */
class PlatformGeometryTest {

    @Test
    void sizeConstants() {
        assertEquals(48, PlatformGeometry.SIDE);
        assertEquals(54, PlatformGeometry.CLEAR_SIDE);
        assertEquals(48 * 48 * 2, PlatformGeometry.platformVolume());
        assertEquals((54 * 54 - 48 * 48) * 2, PlatformGeometry.ringVolume());
    }

    @Test
    void platformBoundsAlignedToChunks() {
        assertEquals(-16, PlatformGeometry.minX(0));
        assertEquals(31, PlatformGeometry.maxX(0));
        assertEquals(-16, PlatformGeometry.minZ(0));
        assertEquals(31, PlatformGeometry.maxZ(0));
    }

    @Test
    void clearBoundsExtendByMargin() {
        assertEquals(-19, PlatformGeometry.clearMinX(0));
        assertEquals(34, PlatformGeometry.clearMaxX(0));
        assertEquals(-19, PlatformGeometry.clearMinZ(0));
        assertEquals(34, PlatformGeometry.clearMaxZ(0));
    }

    @Test
    void inPlatformIsInclusiveOnBothCorners() {
        assertTrue(PlatformGeometry.inPlatform(-16, -16, 0, 0));
        assertTrue(PlatformGeometry.inPlatform(31, 31, 0, 0));
        assertFalse(PlatformGeometry.inPlatform(-17, 0, 0, 0));
        assertFalse(PlatformGeometry.inPlatform(0, 32, 0, 0));
    }

    @Test
    void layersStackDownwardFromAnchor() {
        assertEquals(64, PlatformGeometry.topY(64));
        assertEquals(63, PlatformGeometry.bottomY(64));
    }
}
