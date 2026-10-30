package com.wenxing.wenxingtools.util;

/**
 * 增幅效果的分流规则。
 * <p>
 * 这里是纯判定，不碰任何 Minecraft 类型——所有分支都能在 {@code .rivet/scratch/} 的单文件探针里直接跑，
 * 不需要起服务端（AGENTS §3.6 T1）。
 */
public final class AmpRules {

    private AmpRules() {
    }

    /**
     * 攻击者分类。{@code inPlayerList} 只在 {@code isPlayer} 为真时有意义。
     * <p>
     * 用「在玩家列表里」而不是「是不是 Forge FakePlayer」判假玩家：模组自建的 ServerPlayer 子类
     * 同样应归入假玩家，而硬编码 Forge 内部类会把它们误判成真实玩家。
     */
    public static AttackerKind classify(boolean isPlayer, boolean inPlayerList) {
        if (!isPlayer) {
            return AttackerKind.MOB;
        }
        return inPlayerList ? AttackerKind.REAL_PLAYER : AttackerKind.FAKE_PLAYER;
    }

    /** 等级合并口径：取最大。负数与空输入都视为 0。 */
    public static int max(int... levels) {
        if (levels == null) {
            return 0;
        }
        int max = 0;
        for (int level : levels) {
            if (level > max) {
                max = level;
            }
        }
        return max;
    }

    /** 攻击增幅的吸血只给真实玩家：假玩家没有血量语义，生物吸血等于永久无敌。 */
    public static boolean shouldHeal(AttackerKind kind) {
        return kind == AttackerKind.REAL_PLAYER;
    }

    /** 经验增幅只给真实玩家：假玩家会把经验发进自己的背包然后随实例销毁。 */
    public static boolean shouldAmplifyXp(AttackerKind kind) {
        return kind == AttackerKind.REAL_PLAYER;
    }

    /** 掉落增幅对所有类别生效——这是女仆与科技方块可用的核心。 */
    public static boolean shouldAmplifyDrops(AttackerKind kind) {
        return kind != null;
    }
}
