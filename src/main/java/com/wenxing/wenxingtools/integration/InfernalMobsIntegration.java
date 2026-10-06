package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.lang.reflect.Method;

public final class InfernalMobsIntegration {

    private static final String CORE_CLASS = "atomicstryker.infernalmobs.common.InfernalMobsCore";


    private static final String NBT_MOD = "InfernalMobsMod";

    private static final String NBT_NOT_INFERNAL = "notInfernal";

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

            restored = 20f;
        }
        maxHealth.setBaseValue(restored);
        if (target.getHealth() > restored) {
            target.setHealth(restored);
        }
    }
}
