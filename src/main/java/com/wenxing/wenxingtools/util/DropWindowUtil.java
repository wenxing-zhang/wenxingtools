package com.wenxing.wenxingtools.util;

/**
 * 死亡掉落「窗口」判定：物品实体入世时，判断它是否可能属于刚刚发生的那次击杀。
 * <p>
 * 纯算术、无 Minecraft 依赖，便于用隔离探针（AGENTS §3.6 T1）覆盖边界。
 */
public final class DropWindowUtil {

    /** 窗口长度（tick）。第三方可能在死亡同栈内生成，也可能 server.execute 延后一刻。 */
    public static final int WINDOW_TICKS = 5;

    /** 以死亡点为中心的判定半径（格）。 */
    public static final double RADIUS = 8.0D;

    private DropWindowUtil() {
    }

    /**
     * @param nowTick    物品入世时的游戏时间
     * @param deathTick  记录死亡时的游戏时间
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
