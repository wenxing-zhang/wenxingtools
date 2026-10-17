package com.wenxing.wenxingtools.util;

/**
 * 平台几何：区块对齐 3×3（48×48）与四向外扩 3 格（54×54）的纯整数边界运算。
 * 无 MC 依赖——T1 探针直接编译本文件即可断言。
 */
public final class PlatformGeometry {
    public static final int CHUNKS_PER_SIDE = 3;
    public static final int SIDE = CHUNKS_PER_SIDE * 16;
    public static final int MARGIN = 3;
    public static final int CLEAR_SIDE = SIDE + MARGIN * 2;
    public static final int LAYERS = 2;

    private PlatformGeometry() {
    }

    public static int minX(int centerChunkX) {
        return (centerChunkX - 1) << 4;
    }

    public static int maxX(int centerChunkX) {
        return minX(centerChunkX) + SIDE - 1;
    }

    public static int minZ(int centerChunkZ) {
        return (centerChunkZ - 1) << 4;
    }

    public static int maxZ(int centerChunkZ) {
        return minZ(centerChunkZ) + SIDE - 1;
    }

    public static int clearMinX(int centerChunkX) {
        return minX(centerChunkX) - MARGIN;
    }

    public static int clearMaxX(int centerChunkX) {
        return maxX(centerChunkX) + MARGIN;
    }

    public static int clearMinZ(int centerChunkZ) {
        return minZ(centerChunkZ) - MARGIN;
    }

    public static int clearMaxZ(int centerChunkZ) {
        return maxZ(centerChunkZ) + MARGIN;
    }

    public static boolean inPlatform(int x, int z, int centerChunkX, int centerChunkZ) {
        return x >= minX(centerChunkX) && x <= maxX(centerChunkX)
                && z >= minZ(centerChunkZ) && z <= maxZ(centerChunkZ);
    }

    public static int topY(int anchorY) {
        return anchorY;
    }

    public static int bottomY(int anchorY) {
        return anchorY - 1;
    }

    public static int platformVolume() {
        return SIDE * SIDE * LAYERS;
    }

    public static int ringVolume() {
        return (CLEAR_SIDE * CLEAR_SIDE - SIDE * SIDE) * LAYERS;
    }
}
