package com.wenxing.wenxingtools.util;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;

/**
 * Mixin 启动自检：校验注入目标是否仍存在；失败打 WARN 并关闭对应特性开关，
 * 避免 Mixin require=0 时功能静默退回原版却仍向玩家展示为可用。
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
    private static volatile boolean anyFailure = false;

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

    public static boolean hasFailures() {
        return anyFailure;
    }

    public static void run() {
        int missing = 0;
        // getDestroyProgress 声明在 BlockBehaviour.BlockStateBase，BlockState 本类没有
        if (checkMethod("BlockState.getDestroyProgress",
                net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.class,
                "getDestroyProgress",
                Player.class, net.minecraft.world.level.BlockGetter.class, net.minecraft.core.BlockPos.class) != 0) {
            fixedMiningEnabled = false;
            missing++;
        }
        // 1.21.1 离地惩罚在 getDigSpeed(BlockState, BlockPos)
        if (checkMethod("Player.getDigSpeed",
                Player.class,
                "getDigSpeed",
                BlockState.class, net.minecraft.core.BlockPos.class) != 0) {
            flyMiningIgnoreEnabled = false;
            missing++;
        }
        // 1.21.1 中附魔全名为 static；实例回调曾导致启动崩溃，此处确认签名形态
        if (checkStaticFullname() != 0) {
            enchantNameOverrideEnabled = false;
            missing++;
        }
        boolean slotsOk = checkFieldPresent("ItemCombinerMenu.inputSlots",
                "net.minecraft.world.inventory.ItemCombinerMenu",
                "inputSlots") == 0;
        boolean resultOk = checkFieldPresent("ItemCombinerMenu.resultSlots",
                "net.minecraft.world.inventory.ItemCombinerMenu",
                "resultSlots") == 0;
        if (!slotsOk || !resultOk) {
            anvilFallbackEnabled = false;
            missing++;
        }
        if (checkMethod("LivingEntity.actuallyHurt",
                LivingEntity.class,
                "actuallyHurt",
                net.minecraft.world.damagesource.DamageSource.class, float.class) != 0) {
            purgeVoidRewriteEnabled = false;
            missing++;
        }
        boolean addEffectOk = checkMethod("LivingEntity.addEffect",
                LivingEntity.class,
                "addEffect",
                net.minecraft.world.effect.MobEffectInstance.class) == 0
                && checkMethod("LivingEntity.addEffect(from)",
                LivingEntity.class,
                "addEffect",
                net.minecraft.world.effect.MobEffectInstance.class, net.minecraft.world.entity.Entity.class) == 0;
        boolean forceAddOk = checkMethod("LivingEntity.forceAddEffect",
                LivingEntity.class,
                "forceAddEffect",
                net.minecraft.world.effect.MobEffectInstance.class, net.minecraft.world.entity.Entity.class) == 0;
        if (!addEffectOk || !forceAddOk) {
            purgeEffectBlockEnabled = false;
            missing++;
        }
        boolean isSupportedItemOk = checkMethod("Enchantment.isSupportedItem",
                Enchantment.class,
                "isSupportedItem",
                net.minecraft.world.item.ItemStack.class) == 0;
        boolean canEnchantOk = checkMethod("Enchantment.canEnchant",
                Enchantment.class,
                "canEnchant",
                net.minecraft.world.item.ItemStack.class) == 0;
        if (!isSupportedItemOk || !canEnchantOk) {
            weaponCoverageExpandEnabled = false;
            missing++;
        }
        boolean isMergableOk = checkDeclaredMethod("ItemEntity.isMergable",
                net.minecraft.world.entity.item.ItemEntity.class, "isMergable") == 0;
        boolean tryToMergeOk = checkDeclaredMethod("ItemEntity.tryToMerge",
                net.minecraft.world.entity.item.ItemEntity.class, "tryToMerge",
                net.minecraft.world.entity.item.ItemEntity.class) == 0;
        if (!isMergableOk || !tryToMergeOk) {
            multiStackMergeGuardEnabled = false;
            missing++;
        }

        anyFailure = missing > 0;
        if (missing == 0) {
            WenXingTools.LOGGER.info("[wenxingtools] mixin self-check OK: all injection targets present");
        } else {
            WenXingTools.LOGGER.warn(
                    "[wenxingtools] mixin self-check: {} target group(s) missing; related feature switches closed "
                            + "(fixedMining={}, flyMiningIgnore={}, enchantNameOverride={}, anvilFallback={}, purgeVoidRewrite={}, purgeEffectBlock={}, weaponCoverageExpand={}, multiStackMergeGuard={})",
                    missing, fixedMiningEnabled, flyMiningIgnoreEnabled, enchantNameOverrideEnabled, anvilFallbackEnabled,
                    purgeVoidRewriteEnabled, purgeEffectBlockEnabled, weaponCoverageExpandEnabled, multiStackMergeGuardEnabled);
        }
    }

    private static int checkDeclaredMethod(String label, Class<?> owner, String name, Class<?>... params) {
        try {
            owner.getDeclaredMethod(name, params);
            return 0;
        } catch (NoSuchMethodException e) {
            WenXingTools.LOGGER.warn("[wenxingtools] mixin target missing: {} ({})", label, e.toString());
            return 1;
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[wenxingtools] mixin target check failed: {} ({})", label, t.toString());
            return 1;
        }
    }

    private static int checkMethod(String label, Class<?> owner, String name, Class<?>... params) {
        try {
            Method method = owner.getMethod(name, params);
            if (method == null) {
                WenXingTools.LOGGER.warn("[wenxingtools] mixin target missing: {}", label);
                return 1;
            }
            return 0;
        } catch (NoSuchMethodException e) {
            WenXingTools.LOGGER.warn("[wenxingtools] mixin target missing: {} ({})", label, e.toString());
            return 1;
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[wenxingtools] mixin target check failed: {} ({})", label, t.toString());
            return 1;
        }
    }

    private static int checkStaticFullname() {
        try {
            Method method = Enchantment.class.getDeclaredMethod(
                    "getFullname",
                    net.minecraft.core.Holder.class, int.class);
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                WenXingTools.LOGGER.warn(
                        "[wenxingtools] Enchantment.getFullname is not static; EnchantmentMixin must use static callback");
                return 1;
            }
            return 0;
        } catch (NoSuchMethodException e) {
            WenXingTools.LOGGER.warn("[wenxingtools] mixin target missing: Enchantment.getFullname ({})", e.toString());
            return 1;
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[wenxingtools] mixin target check failed: Enchantment.getFullname ({})", t.toString());
            return 1;
        }
    }

    private static int checkFieldPresent(String label, String className, String fieldName) {
        try {
            Class<?> owner = Class.forName(className);
            owner.getDeclaredField(fieldName);
            return 0;
        } catch (ClassNotFoundException e) {
            WenXingTools.LOGGER.warn("[wenxingtools] mixin target class missing: {} ({})", label, e.toString());
            return 1;
        } catch (NoSuchFieldException e) {
            WenXingTools.LOGGER.warn("[wenxingtools] mixin target field missing: {} ({})", label, e.toString());
            return 1;
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[wenxingtools] mixin target check failed: {} ({})", label, t.toString());
            return 1;
        }
    }
}
