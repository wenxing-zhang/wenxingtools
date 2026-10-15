package com.wenxing.wenxingtools.entity;

/**
 * 飞行座的纯判定策略：不依赖 MC 运行时，可用隔离探针直接验证。
 */
public final class FlightSeatPolicy {
    private FlightSeatPolicy() {}

    /**
     * 起飞（离地）之后再次触地即下马。
     * 未离地时不算落地——否则贴地召唤的下一刻就会失效；贴地且按住上升键时不下马，可原地再起飞。
     */
    public static boolean shouldDismountOnGround(boolean leftGround, boolean touchingGround, int upInput) {
        return touchingGround && leftGround && upInput <= 0;
    }
}
