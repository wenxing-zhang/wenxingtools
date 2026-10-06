package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class L2HostilityIntegration {

    private static final String CAP_CLASS = "dev.xkmc.l2hostility.content.capability.mob.MobTraitCap";
    private static final String TRAIT_CLASS = "dev.xkmc.l2hostility.content.traits.base.MobTrait";

    private static volatile Boolean ready;
    private static final Object INIT_LOCK = new Object();

    private static Class<?> traitClass;
    private static Object holder;
    private static Method holderIsProper;
    private static Method holderGet;
    private static Method setTrait;
    private static Method removeTrait;
    private static Method syncToClient;
    private static Field traitsField;
    private static Field pendingField;

    private L2HostilityIntegration() {
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
                Class<?> capClass = Class.forName(CAP_CLASS);
                traitClass = Class.forName(TRAIT_CLASS);
                holder = capClass.getField("HOLDER").get(null);
                Class<?> holderClass = holder.getClass();

                holderIsProper = findMethod(holderClass, "isProper", 1);
                holderGet = findMethod(holderClass, "get", 1);
                setTrait = capClass.getMethod("setTrait", traitClass, int.class);
                removeTrait = capClass.getMethod("removeTrait", traitClass);
                syncToClient = capClass.getMethod("syncToClient", LivingEntity.class);
                traitsField = capClass.getField("traits");
                try {
                    pendingField = capClass.getDeclaredField("pending");
                    pendingField.setAccessible(true);
                } catch (NoSuchFieldException ignored) {
                    pendingField = null;
                }
                ready = holder != null && holderIsProper != null && holderGet != null;
            } catch (Throwable t) {
                ready = Boolean.FALSE;
                WenXingTools.LOGGER.debug("[effect_purge] L2Hostility MobTraitCap init failed: {}", t.toString());
            }
            return ready;
        }
    }

    private static Method findMethod(Class<?> clazz, String name, int paramCount) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }


    public static void strip(LivingEntity target) {
        if (target == null || !ensureReady()) {
            return;
        }
        try {
            Boolean proper = (Boolean) holderIsProper.invoke(holder, target);
            if (proper == null || !proper) {
                return;
            }
            Object cap = holderGet.invoke(holder, target);
            if (cap == null) {
                return;
            }
            Map<?, ?> traits = (Map<?, ?>) traitsField.get(cap);

            if (traits == null || traits.isEmpty()) {
                return;
            }
            List<Object> keys = new ArrayList<>(traits.keySet());
            for (Object trait : keys) {
                setTrait.invoke(cap, trait, 0);
            }
            for (Object trait : keys) {
                removeTrait.invoke(cap, trait);
            }
            if (pendingField != null) {
                Object pending = pendingField.get(cap);
                if (pending instanceof List<?> list) {
                    list.clear();
                }
            }
            traits.clear();

            syncToClient.invoke(cap, target);
        } catch (Throwable t) {
            WenXingTools.LOGGER.debug("[effect_purge] L2Hostility strip failed: {}", t.toString());
        }
    }


    public static void stripNbtKeys(LivingEntity target) {
        if (target == null) {
            return;
        }
        CompoundTag data = target.getPersistentData();
        for (String key : com.wenxing.wenxingtools.event.EffectPurgeSupport.copyKeys(data)) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.contains("hostility") || lower.contains("affix") || lower.contains("affinit")
                    || lower.contains("l2hostility")) {
                data.remove(key);
            }
        }
    }

    public static boolean isPresent() {
        return ensureReady();
    }

    static Class<?> traitClass() {
        return traitClass;
    }
}
