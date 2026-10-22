package com.wenxing.wenxingtools.entity;

/**
 * 自由飞行的纯判定策略：不依赖 MC 运行时，可用隔离探针直接验证。
 */
public final class FreedomFlightPolicy {
    private FreedomFlightPolicy() {}

    /** 贴地持续多少刻才判定为「落地收起」——留出贴地掠过与贴地起飞的余量。 */
    public static final int GROUND_STOP_TICKS = 5;

    /** 贴地探测深度（格）。 */
    public static final double GROUND_PROBE = 0.08D;

    /** 垂直加速度倍率——复刻原版创造飞行（LocalPlayer 内为 flyingSpeed * 3.0）。 */
    public static final double VERTICAL_ACCEL_FACTOR = 3.0D;

    /** 垂直速度衰减系数——复刻原版 Player.travel 的飞行分支（该刻末 y 取上一刻 y * 0.6）。 */
    public static final double VERTICAL_DECAY = 0.6D;

    /** 是否处于「飞行中贴地且未上升」的状态（该状态每刻累计一次）。 */
    public static boolean countsAsGrounded(boolean flying, boolean touchingGround, boolean rising) {
        return flying && touchingGround && !rising;
    }

    /** 飞行中贴地、且持续够久才收起；未在飞行、或贴地时正在上升都不算。 */
    public static boolean shouldStopOnGround(boolean flying, boolean touchingGround, boolean rising, int groundTicks) {
        return countsAsGrounded(flying, touchingGround, rising) && groundTicks >= GROUND_STOP_TICKS;
    }

    /**
     * 垂直输入：跳跃与潜行相加（与原版创造飞行一致——两者同时按下互相抵消为悬停）。
     * 不能写成「跳跃优先」：那会让「按住潜行键下降时顺手按空格」变成持续上升。
     */
    public static int verticalInput(boolean jumping, boolean shiftDown) {
        int input = 0;
        if (shiftDown) {
            --input;
        }
        if (jumping) {
            ++input;
        }
        return input;
    }

    /** 是否按原版飞行路径处理：原版字段为真，或本模组自由飞行中。 */
    public static boolean effectiveFlying(boolean abilitiesFlying, boolean freedomFlying) {
        return abilitiesFlying || freedomFlying;
    }

    /**
     * 本刻输入是否要套用潜行减速系数。原版创造飞行时 crouching 恒为 false，
     * 慢速判定因此不成立；自由飞行不写该能力字段，必须在这里显式抑制，
     * 否则按住潜行键下降时水平输入会被乘成 30%。
     */
    public static boolean slowdownApplies(boolean movingSlowly, boolean freedomFlying) {
        return movingSlowly && !freedomFlying;
    }

    /** 单刻垂直加速度增量。 */
    public static double verticalAccel(int verticalInput, float flyingSpeed) {
        return (double) verticalInput * (double) flyingSpeed * VERTICAL_ACCEL_FACTOR;
    }

    /** 垂直速度经一刻后的衰减值。 */
    public static double frictionDecay(double previousY) {
        return previousY * VERTICAL_DECAY;
    }
}
