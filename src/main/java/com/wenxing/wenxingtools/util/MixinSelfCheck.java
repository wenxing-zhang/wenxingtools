package com.wenxing.wenxingtools.util;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.mixin.MixinMarkers;

/**
 * Mixin 启动自检：校验注入是否<b>真正落到目标类</b>；失败打 WARN 并关闭对应特性开关，
 * 避免功能静默退回原版却仍向玩家展示为可用。
 * <p>
 * 判据是「落地标记」而不是「目标成员是否存在」：每个 Mixin 类实现 {@link MixinMarkers} 里的一个空标记接口，
 * Mixin 成功应用时会把接口加到目标类上。任一注入点打空——目标成员还在、调用点却没了——都会让整个 Mixin
 * 应用失败、标记随之缺失；这一层是成员存在性检查看不到的（铁砧曾有三条永不命中的重定向即属此类）。
 * <p>
 * 注入点不再写 {@code require = 0}（那会让打空彻底静默）：统一走配置的 defaultRequire，
 * 打空即该 Mixin 整体失败并在启动日志留痕。
 * 工具类：无事件监听，勿加 @EventBusSubscriber。
 */
public final class MixinSelfCheck {
    private MixinSelfCheck() {}

    /** 固定 0.25s 挖掘（BlockStateDestroyProgressMixin） */
    private static volatile boolean fixedMiningEnabled = true;
    /** 飞行/离地挖掘免惩罚（PlayerDestroySpeedMixin） */
    private static volatile boolean flyMiningIgnoreEnabled = true;
    /** 附魔名灰色/超 10 级数字显示（EnchantmentMixin） */
    private static volatile boolean enchantNameOverrideEnabled = true;
    /** 铁砧任意附魔书合并 + 固定 1 级经验（AnvilMenu*Mixin） */
    private static volatile boolean anvilFallbackEnabled = true;
    /** 净化持续窗受击改写虚空（LivingEntityPurgeDamageMixin） */
    private static volatile boolean purgeVoidRewriteEnabled = true;
    /** 净化持续窗拦 addEffect/forceAddEffect（LivingEntityPurgeEffectMixin） */
    private static volatile boolean purgeEffectBlockEnabled = true;
    /** 掠蛋动态武器覆盖（EnchantmentWeaponCoverageMixin） */
    private static volatile boolean weaponCoverageExpandEnabled = true;
    /** MultiStack 防邻居合并（ItemEntityMergeGuardMixin） */
    private static volatile boolean multiStackMergeGuardEnabled = true;
    /** 自由飞行（Player/LocalPlayer 飞行路径接入 + 服务端悬空豁免，三个 Mixin 合为一组开关） */
    private static volatile boolean freedomFlightEnabled = true;

    public static boolean isFixedMiningEnabled() {
        return fixedMiningEnabled;
    }

    public static boolean isFlyMiningIgnoreEnabled() {
        return flyMiningIgnoreEnabled;
    }

    public static boolean isEnchantNameOverrideEnabled() {
        return enchantNameOverrideEnabled;
    }

    public static boolean isAnvilFallbackEnabled() {
        return anvilFallbackEnabled;
    }

    public static boolean isPurgeVoidRewriteEnabled() {
        return purgeVoidRewriteEnabled;
    }

    public static boolean isPurgeEffectBlockEnabled() {
        return purgeEffectBlockEnabled;
    }

    public static boolean isWeaponCoverageExpandEnabled() {
        return weaponCoverageExpandEnabled;
    }

    public static boolean isMultiStackMergeGuardEnabled() {
        return multiStackMergeGuardEnabled;
    }

    public static boolean isFreedomFlightEnabled() {
        return freedomFlightEnabled;
    }

    public static void run() {
        int missing = 0;

        if (!checkMarker("固定 0.25s 挖掘（BlockStateDestroyProgressMixin）",
                "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase",
                MixinMarkers.FixedMining.class)) {
            fixedMiningEnabled = false;
            missing++;
        }
        if (!checkMarker("飞行/离地挖掘免惩罚（PlayerDestroySpeedMixin）",
                "net.minecraft.world.entity.player.Player",
                MixinMarkers.FlyMiningIgnore.class)) {
            flyMiningIgnoreEnabled = false;
            missing++;
        }
        if (!checkMarker("附魔名显示（EnchantmentMixin）",
                "net.minecraft.world.item.enchantment.Enchantment",
                MixinMarkers.EnchantNameDisplay.class)) {
            enchantNameOverrideEnabled = false;
            missing++;
        }
        if (!checkMarker("掠蛋武器覆盖（EnchantmentWeaponCoverageMixin）",
                "net.minecraft.world.item.enchantment.Enchantment",
                MixinMarkers.WeaponCoverageExpand.class)) {
            weaponCoverageExpandEnabled = false;
            missing++;
        }

        // 铁砧由两个 Mixin 分工，各自独立落地；收尾 Mixin 另用反射取父类容器字段，与注入无关，需单独核对
        boolean anvilRedirect = checkMarker("铁砧任意附魔/忽略互斥（AnvilMenuMixin）",
                "net.minecraft.world.inventory.AnvilMenu",
                MixinMarkers.AnvilAnyEnchantment.class);
        boolean anvilFinalize = checkMarker("铁砧结果收尾（AnvilMenuFinalizerMixin）",
                "net.minecraft.world.inventory.AnvilMenu",
                MixinMarkers.AnvilResultFinalize.class);
        boolean inputSlots = checkFieldPresent("ItemCombinerMenu.inputSlots",
                "net.minecraft.world.inventory.ItemCombinerMenu", "inputSlots");
        boolean resultSlots = checkFieldPresent("ItemCombinerMenu.resultSlots",
                "net.minecraft.world.inventory.ItemCombinerMenu", "resultSlots");
        boolean anvilFields = inputSlots && resultSlots;
        if (!anvilRedirect || !anvilFinalize || !anvilFields) {
            anvilFallbackEnabled = false;
            missing++;
        }

        if (!checkMarker("净化受击改写虚空（LivingEntityPurgeDamageMixin）",
                "net.minecraft.world.entity.LivingEntity",
                MixinMarkers.PurgeVoidRewrite.class)) {
            purgeVoidRewriteEnabled = false;
            missing++;
        }
        if (!checkMarker("净化拦效果写入（LivingEntityPurgeEffectMixin）",
                "net.minecraft.world.entity.LivingEntity",
                MixinMarkers.PurgeEffectWriteBlock.class)) {
            purgeEffectBlockEnabled = false;
            missing++;
        }
        if (!checkMarker("多合一防邻居合并（ItemEntityMergeGuardMixin）",
                "net.minecraft.world.entity.item.ItemEntity",
                MixinMarkers.MultiStackMergeGuard.class)) {
            multiStackMergeGuardEnabled = false;
            missing++;
        }

        // 自由飞行由三个 Mixin 共同构成完整链路，任一缺失都不成立；客户端 Mixin 只在客户端侧核对，
        // 否则专用服务端上「目标类未加载导致标记缺失」会被误判为打空。
        boolean freedomTravel = checkMarker("自由飞行-原版飞行路径接入（PlayerFreedomFlightMixin）",
                "net.minecraft.world.entity.player.Player",
                MixinMarkers.FreedomFlightTravel.class);
        boolean freedomGuard = checkMarker("自由飞行-悬空检测豁免（ServerFreedomFlightGuardMixin）",
                "net.minecraft.server.network.ServerGamePacketListenerImpl",
                MixinMarkers.FreedomFlightGuard.class);
        boolean freedomClient = true;
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            freedomClient = checkMarker("自由飞行-客户端垂直输入（LocalPlayerFreedomFlightMixin）",
                    "net.minecraft.client.player.LocalPlayer",
                    MixinMarkers.FreedomFlightClient.class);
        }
        if (!freedomTravel || !freedomGuard || !freedomClient) {
            freedomFlightEnabled = false;
            missing++;
        }

        if (missing == 0) {
            WenXingTools.LOGGER.info("[wenxingtools] mixin self-check OK: all mixin groups applied and marked");
        } else {
            WenXingTools.LOGGER.warn(
                    "[wenxingtools] mixin self-check: {} mixin group(s) not applied; related feature switches closed "
                            + "(fixedMining={}, flyMiningIgnore={}, enchantNameOverride={}, anvilFallback={}, purgeVoidRewrite={}, purgeEffectBlock={}, weaponCoverageExpand={}, multiStackMergeGuard={}, freedomFlight={})",
                    missing, fixedMiningEnabled, flyMiningIgnoreEnabled, enchantNameOverrideEnabled, anvilFallbackEnabled,
                    purgeVoidRewriteEnabled, purgeEffectBlockEnabled, weaponCoverageExpandEnabled, multiStackMergeGuardEnabled,
                    freedomFlightEnabled);
        }
    }

    /**
     * 目标类上是否带着该 Mixin 的落地标记。
     * <p>
     * {@code Class.forName} 会触发目标类加载，从而让 Mixin 在此刻应用：标记在 ⇒ 该 Mixin 的全部注入点均已命中；
     * 标记不在 ⇒ 该 Mixin 未应用（注入打空、或接口注入失败），调用方据此关闭对应特性。
     */
    private static boolean checkMarker(String label, String targetClassName, Class<?> marker) {
        Class<?> target;
        try {
            target = Class.forName(targetClassName);
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[wenxingtools] mixin target class unavailable: {} ({})", label, t.toString());
            return false;
        }
        if (marker.isAssignableFrom(target)) {
            return true;
        }
        WenXingTools.LOGGER.warn("[wenxingtools] mixin not applied: {} (marker {} absent on {})",
                label, marker.getSimpleName(), target.getName());
        return false;
    }

    /** 供 Mixin 自身反射访问的成员（与注入无关，需单独核对）。 */
    private static boolean checkFieldPresent(String label, String className, String fieldName) {
        try {
            Class.forName(className).getDeclaredField(fieldName);
            return true;
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[wenxingtools] reflective target missing: {} ({})", label, t.toString());
            return false;
        }
    }
}
