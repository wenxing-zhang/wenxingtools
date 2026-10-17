package com.wenxing.wenxingtools.util;

/**
 * 平台生成枚举逻辑：只做坐标遍历与判定，方块读写交给调用方 sink。
 * 无 MC 依赖（接口参数均为 int 坐标）——T1 探针可用假世界断言全部边界。
 */
public final class PlatformGenerator {

    public interface BlockSetter {
        void set(int x, int y, int z);
    }

    public interface AirProbe {
        boolean isAir(int x, int y, int z);
    }

    public record Counts(int filled, int fillReplaced, int ringCleared, int aboveCleared, int aboveScanned) {
        public int destroyed() {
            return fillReplaced + ringCleared + aboveCleared;
        }
    }

    private PlatformGenerator() {
    }

    /**
     * 填充 48×48×2 平台层；清除 54×54 外扩环的平台层；清除 54×54 上方至建造上限的全部非空气方块。
     * 只对非空气调用 clear（空气清除是空操作，也不参与掉落结算）。
     */
    public static Counts run(int centerChunkX, int centerChunkZ, int anchorY, int maxBuildHeight,
                             BlockSetter fill, BlockSetter clear, AirProbe probe) {
        int filled = 0;
        int fillReplaced = 0;
        int ringCleared = 0;
        int aboveCleared = 0;
        int aboveScanned = 0;

        int topY = PlatformGeometry.topY(anchorY);
        int bottomY = PlatformGeometry.bottomY(anchorY);
        int minX = PlatformGeometry.minX(centerChunkX);
        int maxX = PlatformGeometry.maxX(centerChunkX);
        int minZ = PlatformGeometry.minZ(centerChunkZ);
        int maxZ = PlatformGeometry.maxZ(centerChunkZ);
        int clearMinX = PlatformGeometry.clearMinX(centerChunkX);
        int clearMaxX = PlatformGeometry.clearMaxX(centerChunkX);
        int clearMinZ = PlatformGeometry.clearMinZ(centerChunkZ);
        int clearMaxZ = PlatformGeometry.clearMaxZ(centerChunkZ);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = bottomY; y <= topY; y++) {
                    if (!probe.isAir(x, y, z)) {
                        fillReplaced++;
                    }
                    fill.set(x, y, z);
                    filled++;
                }
            }
        }

        for (int x = clearMinX; x <= clearMaxX; x++) {
            for (int z = clearMinZ; z <= clearMaxZ; z++) {
                if (PlatformGeometry.inPlatform(x, z, centerChunkX, centerChunkZ)) {
                    continue;
                }
                for (int y = bottomY; y <= topY; y++) {
                    if (probe.isAir(x, y, z)) {
                        continue;
                    }
                    clear.set(x, y, z);
                    ringCleared++;
                }
            }
        }

        for (int x = clearMinX; x <= clearMaxX; x++) {
            for (int z = clearMinZ; z <= clearMaxZ; z++) {
                for (int y = topY + 1; y < maxBuildHeight; y++) {
                    aboveScanned++;
                    if (probe.isAir(x, y, z)) {
                        continue;
                    }
                    clear.set(x, y, z);
                    aboveCleared++;
                }
            }
        }

        return new Counts(filled, fillReplaced, ringCleared, aboveCleared, aboveScanned);
    }

    /** 玩家脚部所在方块层落在平台的两层实心层之内 → 需要上抬 */
    public static boolean playerEmbedded(int feetBlockY, int anchorY) {
        return feetBlockY >= PlatformGeometry.bottomY(anchorY) && feetBlockY <= PlatformGeometry.topY(anchorY);
    }
}
