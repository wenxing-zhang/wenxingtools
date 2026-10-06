package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.integration.InfernalMobsIntegration;
import com.wenxing.wenxingtools.integration.L2HostilityIntegration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

public final class EffectPurgeSupport {


    public static final String PURGE_UNTIL_TAG = WenXingTools.MODID + ":effect_purge_until";


    public static final int CHECK_INTERVAL_TICKS = 20;


    public static final int NUCLEAR_LEVEL = 255;


    public static final int SECONDS_PER_LEVEL = 10;


    private static final ThreadLocal<Boolean> VOID_CONVERTING = ThreadLocal.withInitial(() -> false);


    private static volatile List<Attribute> attributeCache;

    private EffectPurgeSupport() {
    }


    public static void applyOnHit(LivingEntity target, long gameTime, int level) {
        if (target == null || target.level().isClientSide || level <= 0) {
            return;
        }
        long ticks = (long) level * SECONDS_PER_LEVEL * 20L;
        target.getPersistentData().putLong(PURGE_UNTIL_TAG, gameTime + ticks);
        if (level >= NUCLEAR_LEVEL) {
            purgeNuclear(target);
        } else {
            suppressEffects(target);
        }
    }


    public static void suppressEffects(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        clearPurgeableEffects(target);
    }

    private static void clearPurgeableEffects(LivingEntity target) {
        for (Object obj : new ArrayList<>(target.getActiveEffects())) {
            if (!(obj instanceof net.minecraft.world.effect.MobEffectInstance instance)) {
                continue;
            }
            if (isProtectedFromPurge(instance)) {
                continue;
            }
            target.removeEffect(instance.getEffect());
        }
    }

    private static boolean isProtectedFromPurge(net.minecraft.world.effect.MobEffectInstance effect) {
        if (effect == null) {
            return false;
        }
        net.minecraft.resources.ResourceLocation id =
                BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
        return id != null
                && "attributearch".equals(id.getNamespace())
                && "wenxing_bind".equals(id.getPath());
    }


    public static void purgeNuclear(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        clearPurgeableEffects(target);
        stripAttributeModifiers(target);
        InfernalMobsIntegration.strip(target);
        L2HostilityIntegration.strip(target);
        L2HostilityIntegration.stripNbtKeys(target);
    }


    public static boolean isSuppressed(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) {
            return false;
        }
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(PURGE_UNTIL_TAG)) {
            return false;
        }
        return entity.level().getGameTime() < data.getLong(PURGE_UNTIL_TAG);
    }

    public static boolean isVoidConverting() {
        return VOID_CONVERTING.get();
    }

    public static void setVoidConverting(boolean converting) {
        VOID_CONVERTING.set(converting);
    }


    public static boolean tickSuppression(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) {
            return false;
        }
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(PURGE_UNTIL_TAG)) {
            return false;
        }
        long now = entity.level().getGameTime();
        long until = data.getLong(PURGE_UNTIL_TAG);
        if (now >= until) {
            data.remove(PURGE_UNTIL_TAG);
            return false;
        }
        suppressEffects(entity);
        return (now % CHECK_INTERVAL_TICKS) == 0;
    }


    static void stripAttributeModifiers(LivingEntity target) {
        List<Attribute> attributes = attributeCache;
        if (attributes == null) {
            List<Attribute> list = new ArrayList<>();
            for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
                list.add(attribute);
            }
            attributes = List.copyOf(list);
            attributeCache = attributes;
        }
        for (Attribute attribute : attributes) {
            AttributeInstance instance = target.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
                instance.removeModifier(modifier);
            }
        }
    }


    public static long getSuppressUntil(LivingEntity entity) {
        if (entity == null) {
            return 0L;
        }
        return entity.getPersistentData().getLong(PURGE_UNTIL_TAG);
    }


    public static List<String> copyKeys(CompoundTag tag) {
        return List.copyOf(tag.getAllKeys());
    }
}
