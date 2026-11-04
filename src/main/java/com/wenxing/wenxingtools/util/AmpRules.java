package com.wenxing.wenxingtools.util;

/**
 * 增幅效果的分流规则。
 * <p>
 * 这里是纯判定，不碰任何 Minecraft 类型——所有分支都能在单元测试里直接跑，不需要起服务端（AGENTS §3.7 T1）。
 */
public final class AmpRules {

    private AmpRules() {
    }

    /**
     * 攻击者分类。{@code inPlayerList} 只在 {@code isPlayer} 为真时有意义。
     * <p>
     * 用「在服务端玩家列表里」而不是「是不是某个加载器的假玩家类」判假玩家：模组自建的 ServerPlayer
     * 子类同样应归入假玩家；也不用 {@code connection == null}——NeoForge 的假玩家会给自己塞一个空连接。
     */
    public static AttackerKind classify(boolean isPlayer, boolean inPlayerList) {
        if (!isPlayer) {
            return AttackerKind.MOB;
        }
        return classifyPlayer(true, inPlayerList);
    }

    /**
     * 玩家子类分类：服务端引用缺失时按真实玩家处理。
     * <p>
     * {@code ServerPlayer#getServer()} 为 null 只可能是极短的过渡态（尚未入服登记或实例已摘除）。
     * 此时若按假玩家处理，真实玩家的经验增幅与吸血会静默失效、且不留任何日志——这比
     * 「极少数假玩家被多发一次经验」是更差的失败模式，故此处放行。
     */
    public static AttackerKind classifyPlayer(boolean serverPresent, boolean inPlayerList) {
        if (!serverPresent) {
            return AttackerKind.REAL_PLAYER;
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
