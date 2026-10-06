package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * AtomicStryker's Infernal Mobs 软集成：按官方源码剥离精英属性。
 * <p>
 * 真源：{@code atomicstryker.infernalmobs.common.InfernalMobsCore} / {@code MobModifier} / {@code SidedCache}
 * （GitHub AtomicStryker/atomicstrykers-minecraft-mods）。
 * <ul>
 *   <li>运行时链表：{@code SidedCache.getInfernalMobs(level)} → {@code removeEntFromElites}</li>
 *   <li>NBT {@code InfernalMobsMod}：词条串；置为 {@code notInfernal} 可阻断再次生成</li>
 *   <li>NBT {@code infernalMaxHealth} + {@code MAX_HEALTH} base：精英血量膨胀，需还原</li>
 * </ul>
 * 缺失模组时静默降级。
 */
public final class InfernalMobsIntegration {

    private static final String CORE_CLASS = "atomicstryker.infernalmobs.common.InfernalMobsCore";

    /** 真源：InfernalMobsCore#getNBTTag()。 */
    private static final String NBT_MOD = "InfernalMobsMod";
    /** 真源：InfernalMobsCore#getNBTMarkerForNonInfernalEntities()。 */
    private static final String NBT_NOT_INFERNAL = "notInfernal";
    /** 真源：MobModifier#increaseMaxHealthForMobIfNeeded。 */
    private static final String NBT_MAX_HEALTH = "infernalMaxHealth";

    private static volatile Boolean ready;
    private static final Object INIT_LOCK = new Object();

    private static Method removeEntFromElites;
    private static Method getMobModifiers;
    private static Method instanceMethod;
    private static Method getMobClassMaxHealth;

    private InfernalMobsIntegration() {
    }

    private static boolean ensureReady() {
        Boolean cached = ready;
        if (cached != null) {
            return cached;
        }
        synchronized (INIT_LOCK) {
            cached = ready;
            if (cached != null) {
                return cached;
            }
            try {
                Class<?> core = Class.forName(CORE_CLASS);
                removeEntFromElites = core.getMethod("removeEntFromElites", LivingEntity.class);
                getMobModifiers = core.getMethod("getMobModifiers", LivingEntity.class);
                try {
                    instanceMethod = core.getMethod("instance");
                } catch (NoSuchMethodException ignored) {
                    instanceMethod = null;
                }
                try {
                    getMobClassMaxHealth = core.getMethod("getMobClassMaxHealth", LivingEntity.class);
                } catch (NoSuchMethodException ignored) {
                    getMobClassMaxHealth = null;
                }
                ready = removeEntFromElites != null && getMobModifiers != null;
            } catch (Throwable t) {
                ready = Boolean.FALSE;
                WenXingTools.LOGGER.debug("[effect_purge] InfernalMobs init failed: {}", t.toString());
            }
            return ready;
        }
    }

    public static boolean isPresent() {
        return ensureReady();
    }

    /** 目标是否带有 Infernal 词缀数据（含血量膨胀标记）；不触发剥离。 */
    public static boolean hasInfernalData(LivingEntity target) {
        if (target == null) {
            return false;
        }
        CompoundTag data = target.getPersistentData();
        if (data.contains(NBT_MAX_HEALTH)) {
            return true;
        }
        if (!data.contains(NBT_MOD)) {
            return false;
        }
        String mod = data.getString(NBT_MOD);
        return !mod.isEmpty() && !NBT_NOT_INFERNAL.equals(mod);
    }

    /**
     * 剥离 Infernal 精英：摘运行时缓存 + NBT 置 notInfernal 阻断再生 + 还原血量膨胀。
     */
    public static void strip(LivingEntity target) {
        if (target == null || !ensureReady()) {
            return;
        }
        try {
            removeEntFromElites.invoke(null, target);

            CompoundTag data = target.getPersistentData();
            data.remove(NBT_MAX_HEALTH);
            data.putString(NBT_MOD, NBT_NOT_INFERNAL);

            restoreMaxHealthBase(target);

            Object mod = getMobModifiers.invoke(null, target);
            if (mod != null) {
                removeEntFromElites.invoke(null, target);
            }
        } catch (Throwable t) {
            WenXingTools.LOGGER.debug("[effect_purge] InfernalMobs strip failed: {}", t.toString());
            try {
                CompoundTag data = target.getPersistentData();
                data.remove(NBT_MOD);
                data.remove(NBT_MAX_HEALTH);
                data.putString(NBT_MOD, NBT_NOT_INFERNAL);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void restoreMaxHealthBase(LivingEntity target) {
        AttributeInstance maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        float restored = 0f;
        if (instanceMethod != null && getMobClassMaxHealth != null) {
            try {
                Object core = instanceMethod.invoke(null);
                if (core != null) {
                    Object base = getMobClassMaxHealth.invoke(core, target);
                    if (base instanceof Number number) {
                        restored = number.floatValue();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (!(restored > 0f) || Float.isNaN(restored)) {
            return;
        }
        maxHealth.setBaseValue(restored);
        if (target.getHealth() > restored) {
            target.setHealth(restored);
        }
    }
}
