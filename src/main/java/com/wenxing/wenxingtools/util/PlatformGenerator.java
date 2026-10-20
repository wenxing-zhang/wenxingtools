package com.wenxing.wenxingtools.util;

/**
 * 平台生成枚举逻辑：只做坐标遍历与判定，方块读写交给调用方 sink。
 * 无 MC 依赖（接口参数均为 int 坐标）——T1 探针可用假世界断言全部边界（AGENTS §3.7）。
 *
 * <p>{@link Cursor} 是可暂停/续跑的三段游标（状态全为 int），供分批执行复用；
 * {@link #run} 是它的一次性驱动器，两者共用同一条枚举顺序。
 */
public final class PlatformGenerator {

    public interface BlockSetter {
        void set(int x, int y, int z);
    }

    public interface AirProbe {
        boolean isAir(int x, int y, int z);
    }

    public enum Phase {
        FILL, RING, ABOVE, DONE
    }

    public record Counts(int filled, int fillReplaced, int ringCleared, int aboveCleared, int aboveScanned) {
        public int destroyed() {
            return fillReplaced + ringCleared + aboveCleared;
        }
    }

    /** 三段游标：顺序为 填充 → 外扩环 → 上方清除，段内 x→z→y 递增，与 {@link #run} 完全一致 */
    public static final class Cursor {
        private final int centerChunkX;
        private final int centerChunkZ;
        private final int anchorY;
        private final int maxBuildHeight;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final int clearMinX;
        private final int clearMinZ;
        private final int topY;
        private final int bottomY;
        private final int aboveHeight;
        private final long fillTotal;
        private final long ringTotal;
        private final long aboveTotal;

        private Phase phase = Phase.FILL;
        private long index;
        private int x;
        private int y;
        private int z;

        public Cursor(int centerChunkX, int centerChunkZ, int anchorY, int maxBuildHeight) {
            this.centerChunkX = centerChunkX;
            this.centerChunkZ = centerChunkZ;
            this.anchorY = anchorY;
            this.maxBuildHeight = maxBuildHeight;
            this.minX = PlatformGeometry.minX(centerChunkX);
            this.maxX = PlatformGeometry.maxX(centerChunkX);
            this.minZ = PlatformGeometry.minZ(centerChunkZ);
            this.maxZ = PlatformGeometry.maxZ(centerChunkZ);
            this.clearMinX = PlatformGeometry.clearMinX(centerChunkX);
            this.clearMinZ = PlatformGeometry.clearMinZ(centerChunkZ);
            this.topY = PlatformGeometry.topY(anchorY);
            this.bottomY = PlatformGeometry.bottomY(anchorY);
            this.aboveHeight = Math.max(0, maxBuildHeight - topY - 1);
            this.fillTotal = (long) PlatformGeometry.SIDE * PlatformGeometry.SIDE * PlatformGeometry.LAYERS;
            this.ringTotal = (long) PlatformGeometry.CLEAR_SIDE * PlatformGeometry.CLEAR_SIDE * PlatformGeometry.LAYERS;
            this.aboveTotal = (long) PlatformGeometry.CLEAR_SIDE * PlatformGeometry.CLEAR_SIDE * aboveHeight;
        }

        public Phase phase() {
            return phase;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int z() {
            return z;
        }

        public int anchorY() {
            return anchorY;
        }

        public int maxBuildHeight() {
            return maxBuildHeight;
        }

        /** 需要处理的格子总数（外扩环里落在平台内的格子会被跳过，不计入） */
        public long plannedCells() {
            return fillTotal + ringTotal - (long) PlatformGeometry.SIDE * PlatformGeometry.SIDE * PlatformGeometry.LAYERS
                    + aboveTotal;
        }

        /** 推进到下一个待处理格子；已无格子时返回 false 并停在 {@link Phase#DONE} */
        public boolean advance() {
            while (true) {
                if (phase == Phase.DONE) {
                    return false;
                }
                long total = totalOf(phase);
                if (index >= total) {
                    phase = nextPhase(phase);
                    index = 0;
                    continue;
                }
                resolve(phase, index);
                index++;
                if (phase == Phase.RING && PlatformGeometry.inPlatform(x, z, centerChunkX, centerChunkZ)) {
                    continue;
                }
                return true;
            }
        }

        private long totalOf(Phase p) {
            return switch (p) {
                case FILL -> fillTotal;
                case RING -> ringTotal;
                case ABOVE -> aboveTotal;
                default -> 0L;
            };
        }

        private static Phase nextPhase(Phase p) {
            return switch (p) {
                case FILL -> Phase.RING;
                case RING -> Phase.ABOVE;
                default -> Phase.DONE;
            };
        }

        private void resolve(Phase p, long idx) {
            if (p == Phase.FILL) {
                long column = idx / PlatformGeometry.LAYERS;
                int layer = (int) (idx % PlatformGeometry.LAYERS);
                x = minX + (int) (column / PlatformGeometry.SIDE);
                z = minZ + (int) (column % PlatformGeometry.SIDE);
                y = bottomY + layer;
            } else if (p == Phase.RING) {
                long column = idx / PlatformGeometry.LAYERS;
                int layer = (int) (idx % PlatformGeometry.LAYERS);
                x = clearMinX + (int) (column / PlatformGeometry.CLEAR_SIDE);
                z = clearMinZ + (int) (column % PlatformGeometry.CLEAR_SIDE);
                y = bottomY + layer;
            } else {
                long column = idx / aboveHeight;
                int offset = (int) (idx % aboveHeight);
                x = clearMinX + (int) (column / PlatformGeometry.CLEAR_SIDE);
                z = clearMinZ + (int) (column % PlatformGeometry.CLEAR_SIDE);
                y = topY + 1 + offset;
            }
        }
    }

    private PlatformGenerator() {
    }

    /**
     * 一次性驱动器：填充 48×48×2 平台层；清除 54×54 外扩环的平台层；清除 54×54 上方至建造上限的全部非空气方块。
     * 只对非空气调用 clear（空气清除是空操作，也不参与掉落结算）。
     */
    public static Counts run(int centerChunkX, int centerChunkZ, int anchorY, int maxBuildHeight,
                             BlockSetter fill, BlockSetter clear, AirProbe probe) {
        Cursor cursor = new Cursor(centerChunkX, centerChunkZ, anchorY, maxBuildHeight);
        int filled = 0;
        int fillReplaced = 0;
        int ringCleared = 0;
        int aboveCleared = 0;
        int aboveScanned = 0;

        while (cursor.advance()) {
            int x = cursor.x();
            int y = cursor.y();
            int z = cursor.z();
            switch (cursor.phase()) {
                case FILL -> {
                    if (!probe.isAir(x, y, z)) {
                        fillReplaced++;
                    }
                    fill.set(x, y, z);
                    filled++;
                }
                case RING -> {
                    if (!probe.isAir(x, y, z)) {
                        clear.set(x, y, z);
                        ringCleared++;
                    }
                }
                case ABOVE -> {
                    aboveScanned++;
                    if (!probe.isAir(x, y, z)) {
                        clear.set(x, y, z);
                        aboveCleared++;
                    }
                }
                default -> {
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
