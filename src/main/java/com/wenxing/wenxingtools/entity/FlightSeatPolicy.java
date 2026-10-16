package com.wenxing.wenxingtools.entity;

/**
 * 飞行座的纯判定策略：不依赖 MC 运行时，可用隔离探针直接验证。
 */
public final class FlightSeatPolicy {
    private FlightSeatPolicy() {}

    /** 贴地持续多少刻才判定为「落地」——留出贴地掠过与贴地起飞的余量。 */
    public static final int GROUND_DISMOUNT_TICKS = 5;

    /** 是否处于「起飞后贴地且未按上升键」的状态（该状态每刻累计一次）。 */
    public static boolean countsAsGrounded(boolean leftGround, boolean touchingGround, int upInput) {
        return touchingGround && leftGround && upInput <= 0;
    }

    /** 起飞后贴地、且持续够久才下马；从未离地或贴地时按着上升键都不算。 */
    public static boolean shouldDismountOnGround(boolean leftGround, boolean touchingGround, int upInput, int groundTicks) {
        return countsAsGrounded(leftGround, touchingGround, upInput) && groundTicks >= GROUND_DISMOUNT_TICKS;
    }
}
