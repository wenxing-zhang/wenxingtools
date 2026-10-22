package com.wenxing.wenxingtools.util;

/**
 * 矿石矩形几何：长 {@link #SIDE} × 高 {@link #SIDE} × 宽 {@link #ORE_KINDS}，每种矿石各占一层 3×3 面。
 *
 * <p>全 int、无 MC 依赖——几何契约可用单元测试逐条断言，不必引入游戏运行时。
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
        return index % SIDE - SIDE / 2;
    }

    public static int yOffset(int index) {
        return (index / SIDE) % SIDE - SIDE / 2;
    }

    /** 11 层沿 Z 铺开，层序即矿石池下标序。 */
    public static int zOffset(int index) {
        return oreIndexOf(index) - LAYER_HALF;
    }

    public static int oreIndexOf(int index) {
        return index / LAYER_CELLS;
    }
}
