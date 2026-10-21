package com.wenxing.wenxingtools.util;

/**
 * 平台几何：按区块档位求水平边界。破坏范围与平台自身水平范围同宽，不再有外扩环。
 *
 * <p>无 MC 依赖（除 {@link ChunkScope} 的接口签名）——T1 探针直接编译本文件即可断言。
 */
public final class PlatformGeometry {
    public static final int LAYERS = 2;
    public static final int ABOVE_LAYERS = 16;

    private PlatformGeometry() {
    }

    public static int minX(int centerChunkX, ChunkScope scope) {
        return (centerChunkX - scope.radiusChunks()) << 4;
    }

    public static int maxX(int centerChunkX, ChunkScope scope) {
        return minX(centerChunkX, scope) + scope.blocks() - 1;
    }

    public static int minZ(int centerChunkZ, ChunkScope scope) {
        return (centerChunkZ - scope.radiusChunks()) << 4;
    }

    public static int maxZ(int centerChunkZ, ChunkScope scope) {
        return minZ(centerChunkZ, scope) + scope.blocks() - 1;
    }

    public static int topY(int anchorY) {
        return anchorY;
    }

    public static int bottomY(int anchorY) {
        return anchorY - 1;
    }

    /** 上方清除层数：满 {@link #ABOVE_LAYERS} 层，触到建造上限时截断 */
    public static int aboveHeight(int topY, int maxBuildHeight) {
        return Math.max(0, Math.min(ABOVE_LAYERS, maxBuildHeight - topY - 1));
    }
}
