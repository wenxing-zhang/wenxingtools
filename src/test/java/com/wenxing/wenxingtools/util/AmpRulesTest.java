package com.wenxing.wenxingtools.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 增幅分流规则（AGENTS §3.7 T1 常驻化）。
 * <p>攻击者分类与「谁吃经验/吸血/掉落」的口径是跨端约定：分类写错会让假玩家漏成真实玩家（经验被盗刷），
 * 吸血判错会让生物变成永久无敌。逐条锁住边界。
 */
class AmpRulesTest {

    @Test
    void nonPlayerIsAlwaysMob() {
        assertEquals(AttackerKind.MOB, AmpRules.classify(false, false));
        // 非玩家传 inPlayerList=true 也不该被认成玩家：布尔参数只在 isPlayer 为真时有意义
        assertEquals(AttackerKind.MOB, AmpRules.classify(false, true));
    }

    @Test
    void playerInListIsRealPlayerAndAbsentIsFake() {
        assertEquals(AttackerKind.REAL_PLAYER, AmpRules.classify(true, true));
        assertEquals(AttackerKind.FAKE_PLAYER, AmpRules.classify(true, false));
    }

    @Test
    void maxTakesHighestSlotLevel() {
        assertEquals(0, AmpRules.max());
        assertEquals(0, AmpRules.max(0, 0, 0));
        assertEquals(3, AmpRules.max(1, 3, 2));
        // 负数（异常组件 / 越界等级）不得压过 0
        assertEquals(2, AmpRules.max(-5, 2));
        assertEquals(0, AmpRules.max(-1, -2));
        assertEquals(5, AmpRules.max(new int[] {5}));
    }

    @Test
    void healOnlyForRealPlayers() {
        assertTrue(AmpRules.shouldHeal(AttackerKind.REAL_PLAYER));
        assertFalse(AmpRules.shouldHeal(AttackerKind.FAKE_PLAYER));
        assertFalse(AmpRules.shouldHeal(AttackerKind.MOB));
    }

    @Test
    void xpOnlyForRealPlayers() {
        assertTrue(AmpRules.shouldAmplifyXp(AttackerKind.REAL_PLAYER));
        assertFalse(AmpRules.shouldAmplifyXp(AttackerKind.FAKE_PLAYER));
        assertFalse(AmpRules.shouldAmplifyXp(AttackerKind.MOB));
    }

    @Test
    void dropsForAllKinds() {
        assertTrue(AmpRules.shouldAmplifyDrops(AttackerKind.REAL_PLAYER));
        assertTrue(AmpRules.shouldAmplifyDrops(AttackerKind.FAKE_PLAYER));
        assertTrue(AmpRules.shouldAmplifyDrops(AttackerKind.MOB));
        // 没有攻击者（未解析出来）不放大：调用方据此跳过整段
        assertFalse(AmpRules.shouldAmplifyDrops(null));
    }
}
