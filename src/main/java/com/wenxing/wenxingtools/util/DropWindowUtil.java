package com.wenxing.wenxingtools.util;

/**
 * 死亡掉落「窗口」判定：物品实体入世时，判断它是否可能属于刚刚发生的那次击杀。
 * <p>
 * 纯算术、无 Minecraft 依赖，便于用隔离探针（AGENTS §3.7 T1）覆盖边界。
 */
public final class DropWindowUtil {

    /**
     * 窗口长度（tick）。{@code 1} = 死亡当刻与下一 tick。
     * <p>
     * 窗口是**推断**归属：窗口越长，被误认成击杀产物的无关落地物越多（方块破坏掉落、物流模组输出都是
     * 无 thrower 的普通物品实体，且都在同一时空里入世）。收窄到「当刻 + 下一 tick」覆盖当场生成与
     * {@code server.execute} / 排程一刻后的生成，同时把误伤面压到最小。
     * <p>
     * 若要退回更宽的覆盖（例如第三方延后才生成、需要 5 tick），把本常量改回 {@code 5} 并把
     * {@link #RADIUS} 改回 {@code 8.0D} 即可，其余代码无需改动。
     */
    public static final int WINDOW_TICKS = 1;

    /** 以死亡点为中心的判定半径（格）。Boss 碰撞箱边缘生成也落在其内，再放宽只会扩大误伤面。 */
    public static final double RADIUS = 4.0D;

    private DropWindowUtil() {
    }

    /**
     * @param nowTick     物品入世时的游戏时间
     * @param deathTick   记录死亡时的游戏时间
     * @param distanceSqr 物品与死亡点距离的平方
     */
    public static boolean inWindow(long nowTick, long deathTick, double distanceSqr) {
        if (nowTick < deathTick) {
            return false;
        }
        if (nowTick - deathTick > WINDOW_TICKS) {
            return false;
        }
        if (Double.isNaN(distanceSqr)) {
            return false;
        }
        return distanceSqr <= RADIUS * RADIUS;
    }
}
