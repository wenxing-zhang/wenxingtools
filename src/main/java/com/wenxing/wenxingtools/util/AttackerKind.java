package com.wenxing.wenxingtools.util;

/**
 * 攻击者类别。
 * <p>
 * 分层的理由：真实玩家、假玩家（科技方块 / 模组自建 ServerPlayer 子类）、生物在「能不能吃到附魔」上同权，
 * 但在「要不要回血」「要不要倍增经验」上不同权。把类别显式建模，调用点就不必各自做 {@code instanceof}
 * 拼装语义，也就不会出现各处口径漂移。
 */
public enum AttackerKind {
    REAL_PLAYER,
    FAKE_PLAYER,
    MOB
}
