package com.wenxing.wenxingtools.entity;

import com.wenxing.wenxingtools.util.EntryBudget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自由飞行的纯判定契约（AGENTS §3.7 T1）。断言逐条对照 1.21.1 原版对应实现：
 * 垂直输入取 LocalPlayer.aiStep 的「潜行 -1、跳跃 +1 相加」，飞行判定取 Player 各处
 * {@code abilities.flying} 的读值语义，慢速系数取 LocalPlayer.aiStep 的 input.tick 前置判定。
 */
class FreedomFlightPolicyTest {

    @Test
    void entryBudgetCarriesMillionsInOneEntry() {
        assertEquals(1_000_000, EntryBudget.saturate(1_000_000L), "百万计数原样承载，不按最大堆叠拆");
        assertEquals(Integer.MAX_VALUE, EntryBudget.saturate(Long.MAX_VALUE), "超出 int 上限：饱和，不回绕成负");
        assertEquals(0, EntryBudget.saturate(-5L), "负值归零");
        assertEquals(2_000_000, EntryBudget.merge(1_000_000, 1_000_000), "同款相加");
        assertEquals(Integer.MAX_VALUE, EntryBudget.merge(Integer.MAX_VALUE, 1), "相加饱和");
    }

    @Test
    void verticalInputAddsBothKeysSoTheyCancelOut() {
        assertEquals(0, FreedomFlightPolicy.verticalInput(false, false), "两键都不按：悬停");
        assertEquals(1, FreedomFlightPolicy.verticalInput(true, false), "只按跳跃：上升");
        assertEquals(-1, FreedomFlightPolicy.verticalInput(false, true), "只按潜行：下降");
        assertEquals(0, FreedomFlightPolicy.verticalInput(true, true),
                "同时按：相加抵消为悬停（跳跃优先会让下降途中按跳跃变成持续上升）");
    }

    @Test
    void effectiveFlyingCoversBothSources() {
        assertFalse(FreedomFlightPolicy.effectiveFlying(false, false), "两处都为假：不在飞行中");
        assertTrue(FreedomFlightPolicy.effectiveFlying(true, false), "原版飞行：按飞行处理");
        assertTrue(FreedomFlightPolicy.effectiveFlying(false, true), "自由飞行：按飞行处理");
        assertTrue(FreedomFlightPolicy.effectiveFlying(true, true), "两处都为真：按飞行处理");
    }

    @Test
    void slowdownOnlyAppliesWhenNotFlying() {
        assertTrue(FreedomFlightPolicy.slowdownApplies(true, false), "地面潜行：套用慢速系数");
        assertFalse(FreedomFlightPolicy.slowdownApplies(true, true),
                "自由飞行中按住潜行键下降：不套用慢速系数（原版创造飞行 crouching 恒为 false）");
        assertFalse(FreedomFlightPolicy.slowdownApplies(false, false), "未潜行：不套用");
        assertFalse(FreedomFlightPolicy.slowdownApplies(false, true), "飞行且未潜行：不套用");
    }

    @Test
    void groundedCountingNeedsFlyingWithoutRising() {
        assertTrue(FreedomFlightPolicy.countsAsGrounded(true, true, false), "飞行中贴地且未上升：累计");
        assertFalse(FreedomFlightPolicy.countsAsGrounded(true, true, true), "贴地仍在上冲：不累计");
        assertFalse(FreedomFlightPolicy.countsAsGrounded(true, false, false), "未贴地：不累计");
        assertFalse(FreedomFlightPolicy.countsAsGrounded(false, true, false), "未飞行：不累计");
    }

    @Test
    void stopOnGroundRequiresFullWindow() {
        int ticks = FreedomFlightPolicy.GROUND_STOP_TICKS;
        assertEquals(5, ticks);
        assertFalse(FreedomFlightPolicy.shouldStopOnGround(true, true, false, ticks - 1), "4 刻：贴地掠过的余量内");
        assertTrue(FreedomFlightPolicy.shouldStopOnGround(true, true, false, ticks), "5 刻：收起");
        assertFalse(FreedomFlightPolicy.shouldStopOnGround(true, true, true, ticks), "贴地仍在上升：不收");
    }

    @Test
    void verticalAccelAndDecayMatchVanillaFlightPath() {
        assertEquals(0.0D, FreedomFlightPolicy.verticalAccel(0, 0.05F), "无垂直输入：无加速度");
        // 容差按 float 精度取：0.05F 转 double 是 0.05000000074505806，乘 3 得 0.15000000223517418，
        // 与 0.15 差 2.2E-9。用 1.0E-12 必然失败，但那衡量的是 float 表示误差，不是公式错。
        assertEquals(0.15D, FreedomFlightPolicy.verticalAccel(1, 0.05F), 1.0E-7, "上升：flyingSpeed * 3");
        assertEquals(-0.15D, FreedomFlightPolicy.verticalAccel(-1, 0.05F), 1.0E-7, "下降：对称取负");
    }

    @Test
    void hoverBrakeDecaysAndSnapsToZero() {
        // 仅覆盖制动目标值；travel 末的再衰减属运行时行为，不在纯函数层断言。
        assertEquals(0.0D, FreedomFlightPolicy.hoverBrake(0.0D), 1.0E-12, "静止：保持静止");
        assertEquals(0.3D, FreedomFlightPolicy.hoverBrake(0.5D), 1.0E-12, "上升残余：按 0.6 衰减");
        assertEquals(-0.3D, FreedomFlightPolicy.hoverBrake(-0.5D), 1.0E-12, "下降残余：对称衰减");
        assertEquals(0.0D, FreedomFlightPolicy.hoverBrake(0.02D), 1.0E-12, "衰减后低于 0.03：归零");
        assertEquals(0.0D, FreedomFlightPolicy.hoverBrake(-0.02D), 1.0E-12, "下降侧同样归零");
        assertEquals(0.036D, FreedomFlightPolicy.hoverBrake(0.06D), 1.0E-12, "衰减后高于 0.03：保留");
    }
}
