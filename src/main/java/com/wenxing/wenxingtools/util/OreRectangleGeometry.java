package com.wenxing.wenxingtools.util;

/**
 * 矿石矩形几何：长 {@link #SIDE} × 高 {@link #SIDE} × 宽 {@link #ORE_KINDS}，每种矿石各占一层 3×3 面。
 *
 * <p>全 int、无 MC 依赖——几何契约、世界边界裁剪与区块归属全部可用单元测试逐条断言。
 */
public final class OreRectangleGeometry {
    public static final int SIDE = 3;
    public static final int ORE_KINDS = 11;
    public static final int LAYER_CELLS = SIDE * SIDE;
    public static final int LAYER_HALF = (ORE_KINDS - 1) / 2;

    private OreRectangleGeometry() {
    }

    public static int cellCount() {
        return LAYER_CELLS * ORE_KINDS;
    }

    /** 索引序：{@code index = 层 * 9 + (dy + 1) * 3 + (dx + 1)} */
    public static int xOffset(int index) {
        requireValidIndex(index);
        return index % SIDE - SIDE / 2;
    }

    public static int yOffset(int index) {
        requireValidIndex(index);
        return (index / SIDE) % SIDE - SIDE / 2;
    }

    /** 11 层沿 Z 铺开，层序即矿石池下标序。 */
    public static int zOffset(int index) {
        requireValidIndex(index);
        return oreIndexOf(index) - LAYER_HALF;
    }

    public static int oreIndexOf(int index) {
        requireValidIndex(index);
        return index / LAYER_CELLS;
    }

    /** 世界可写 Y 区间为 {@code [minY, maxY)}：maxY 是开区间上界（建造上限本身不可写）。 */
    public static boolean inBuildRange(int y, int minY, int maxY) {
        return y >= minY && y < maxY;
    }

    /**
     * 方块坐标所属区块坐标。用算术右移与 {@code Math.floorDiv(coord, 16)} 等值，
     * 负数坐标（西北象限）不会被算偏——{@code -1 >> 4 == -1} 而不是 0。
     */
    public static int chunkOf(int blockCoord) {
        return blockCoord >> 4;
    }

    /**
     * 越界索引直接抛错：纯几何类作为公共契约，静默算出锚点外的偏移比抛错更难排查
     * （{@code -1 % 3 == -1} 会把写入点挪到锚点外 2 格）。
     */
    private static void requireValidIndex(int index) {
        if (index < 0 || index >= cellCount()) {
            throw new IllegalArgumentException("矿石矩形索引越界: " + index + "，合法范围 0.." + (cellCount() - 1));
        }
    }
}
