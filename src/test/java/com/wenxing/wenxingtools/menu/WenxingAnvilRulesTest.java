package com.wenxing.wenxingtools.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * wenxing铁砧规则层契约（AGENTS §3.7 T1 常驻化）。
 *
 * <p>覆盖三条不变量：同物品合并的耐久口径（原版「耐久和 + 12%」，且永不比左件更差）、
 * 附魔合并（不降级 / 不越组件 255 上界 / 不溢出）、产出与代价的一致性。
 */
class WenxingAnvilRulesTest {

    // ── 耐久合并：原版 createResult 的「耐久和 + 12%」口径 ──

    @Test
    void mergedDamageUsesDurabilitySumNotDamageSum() {
        // 两件各损一半：耐久和 50+50 + 12% → 修满
        assertEquals(0, WenxingAnvilRules.mergedDamage(50, 50, 100));
        // 旧错误口径（损伤相加 − 12%）会得到 88——方向反了
        assertEquals(88, WenxingAnvilRules.mergedDamage(100, 100, 100));
        assertEquals(0, WenxingAnvilRules.mergedDamage(0, 0, 100));
    }

    @Test
    void mergedDamageNeverWorseThanLeftAndStaysInRange() {
        for (int max = 1; max <= 400; max += 3) {
            for (int dl = 0; dl <= max; dl += Math.max(1, max / 11)) {
                for (int dr = 0; dr <= max; dr += Math.max(1, max / 9)) {
                    int out = WenxingAnvilRules.mergedDamage(dl, dr, max);
                    assertTrue(out <= dl, "合并结果不得比左件更差: max=" + max + " dl=" + dl + " dr=" + dr);
                    assertTrue(out >= 0, "损伤不得为负: max=" + max + " out=" + out);
                    assertTrue(out <= max - 1, "损伤不得达到 maxDamage: max=" + max + " out=" + out);
                    assertEquals(out, WenxingAnvilRules.mergedDamage(dr, dl, max), "左右交换应等价");
                }
            }
        }
    }

    @Test
    void mergedDamageHandlesDegenerateMaxDamage() {
        assertEquals(0, WenxingAnvilRules.mergedDamage(5, 5, 0));
        assertEquals(0, WenxingAnvilRules.mergedDamage(0, 0, 1));
    }

    // ── 附魔合并 ──

    @Test
    void mergeLevelUpgradesSameLevelAndTakesMax() {
        assertEquals(3, WenxingAnvilRules.mergeLevel(2, 2, 5));
        assertEquals(3, WenxingAnvilRules.mergeLevel(1, 3, 5));
        assertEquals(5, WenxingAnvilRules.mergeLevel(5, 5, 5));
        assertEquals(4, WenxingAnvilRules.mergeLevel(4, 0, 5));
        assertEquals(1, WenxingAnvilRules.mergeLevel(0, 1, 5));
    }

    @Test
    void mergeLevelNeverDowngradesOnAnyBranch() {
        // 缺失源等级的分支同样不得降级（历史缺陷：早退分支会把高于上限的等级钳回 cap）
        assertEquals(7, WenxingAnvilRules.mergeLevel(7, 1, 5));
        assertEquals(2, WenxingAnvilRules.mergeLevel(2, 0, 1));
        assertEquals(2, WenxingAnvilRules.mergeLevel(2, -5, 1));
        assertEquals(3, WenxingAnvilRules.mergeLevel(3, 3, 1));
    }

    @Test
    void mergeLevelStaysWithinComponentBound() {
        int[] inputs = {-5, 0, 1, 2, 254, 255, 256, 1000, Integer.MAX_VALUE};
        int[] caps = {-3, 0, 1, 5, 255, 256, Integer.MAX_VALUE};
        for (int current : inputs) {
            for (int source : inputs) {
                for (int cap : caps) {
                    int out = WenxingAnvilRules.mergeLevel(current, source, cap);
                    assertTrue(out >= 0, "等级不得为负: cur=" + current + " src=" + source + " cap=" + cap);
                    assertTrue(out <= WenxingAnvilRules.MAX_ENCHANTMENT_LEVEL,
                            "等级不得越组件 255 上界: cur=" + current + " src=" + source + " cap=" + cap + " out=" + out);
                    if (current > 0) {
                        assertTrue(out >= Math.min(current, WenxingAnvilRules.MAX_ENCHANTMENT_LEVEL),
                                "不得降级: cur=" + current + " src=" + source + " cap=" + cap + " out=" + out);
                    }
                }
            }
        }
    }

    // ── 改名归属 ──

    @Test
    void shouldApplyNameRequiresOwnership() {
        assertTrue(WenxingAnvilRules.shouldApplyName(true, false, false));
        assertFalse(WenxingAnvilRules.shouldApplyName(false, false, false), "名字不绑定当前左件时不得套用");
        assertFalse(WenxingAnvilRules.shouldApplyName(true, true, false), "空名字不应用");
        assertFalse(WenxingAnvilRules.shouldApplyName(true, false, true), "与当前名相同不应用");
    }

    @Test
    void nameLengthBoundaryMatchesVanilla() {
        assertEquals(50, WenxingAnvilRules.MAX_NAME_LENGTH);
        assertTrue(WenxingAnvilRules.isNameAcceptable("x".repeat(50)));
        assertFalse(WenxingAnvilRules.isNameAcceptable("x".repeat(51)));
        assertTrue(WenxingAnvilRules.isNameAcceptable(""));
        assertFalse(WenxingAnvilRules.isNameAcceptable(null));
    }

    // ── 产出与代价 ──

    @Test
    void shouldProduceOnlyWithAUsableRightSlotAndRealChange() {
        assertTrue(WenxingAnvilRules.shouldProduce(true, true, false, false, true));
        assertTrue(WenxingAnvilRules.shouldProduce(true, false, true, false, true));
        assertTrue(WenxingAnvilRules.shouldProduce(true, false, false, true, true));
        assertFalse(WenxingAnvilRules.shouldProduce(true, true, false, false, false), "无变化不产出");
        assertFalse(WenxingAnvilRules.shouldProduce(true, false, false, false, true), "右槽异物不产出（不吞材料）");
        assertFalse(WenxingAnvilRules.shouldProduce(false, true, false, false, true), "左槽不可用不产出");
    }

    @Test
    void costAlwaysMatchesOutputPresence() {
        for (int mask = 0; mask < 32; mask++) {
            boolean usable = (mask & 1) != 0;
            boolean empty = (mask & 2) != 0;
            boolean book = (mask & 4) != 0;
            boolean mergeable = (mask & 8) != 0;
            boolean changed = (mask & 16) != 0;
            boolean produce = WenxingAnvilRules.shouldProduce(usable, empty, book, mergeable, changed);
            assertEquals(produce ? 1 : 0, WenxingAnvilRules.costFor(produce));
            if (produce) {
                assertTrue(usable && changed);
            }
        }
        assertEquals(1, WenxingAnvilRules.COST_XP_POINTS);
    }
}
