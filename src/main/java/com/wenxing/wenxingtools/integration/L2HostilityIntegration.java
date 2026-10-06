package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.event.EffectPurgeSupport;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * L2Hostility（恶意）软集成：清除 Mob 词条（Trait）。
 * <p>
 * 真源：Minecraft-LightLand/L2Hostility 分支 {@code 1.21}（NeoForge 1.21.1）。
 * <ul>
 *   <li>能力入口：NeoForge {@code AttachmentType}（{@code l2hostility:mob} 等），
 *       或 {@code LHMiscs.MOB.type().getExisting/getOrCreate}（L2Library 3.x）。
 *       <b>1.21 已无 {@code MobTraitCap.HOLDER}</b>（旧反射路径会永久 init 失败）。</li>
 *   <li>清除语义对齐 {@code RemoveTraitEnchantment}：{@code removeTrait} + {@code syncToClient}（立即）。
 *       命令 {@code commandClearTrait} 的 {@code setTrait(trait, 0)} 仅写入 {@code pending}，要等下 tick
 *       {@code clearPending} 才落地——因此净化路径以 {@code removeTrait} + 直接清 {@code traits} 为准。</li>
 *   <li>{@code AttributeTrait} 属性挂在 {@code l2hostility:*} 修饰器上，词条移除后仍会残留，需一并摘除。</li>
 * </ul>
 * 初始化失败或清除失败会打 <b>WARN 一次</b>（不再静默吞掉）。
 */
public final class L2HostilityIntegration {

    private static final String CAP_CLASS = "dev.xkmc.l2hostility.content.capability.mob.MobTraitCap";
    private static final String LH_MISCS_CLASS = "dev.xkmc.l2hostility.init.registrate.LHMiscs";
    private static final String[] TRAIT_CLASS_NAMES = {
            "dev.xkmc.l2hostility.content.traits.base.MobTrait",
            "dev.xkmc.l2hostility.content.traits.MobTrait"
    };
    /** L2 1.21 AttReg.entity("mob", ...)；扫描兜底。 */
    private static final String[] ATTACHMENT_IDS = {
            "l2hostility:mob",
            "l2hostility:entity_mob",
            "l2hostility:mob_trait"
    };

    /** 0=未探测，1=L2 已安装，2=未安装。 */
    private static volatile int installState;
    /** 能力访问是否已解析成功（可重试，不因注册表未就绪而永久失败）。 */
    private static volatile boolean accessReady;
    private static final Object INIT_LOCK = new Object();
    private static final AtomicBoolean WARNED_STRIP = new AtomicBoolean();
    private static final AtomicBoolean WARNED_ACCESS = new AtomicBoolean();

    private static Class<?> capClass;
    private static Class<?> traitClass;

    // 路径 A：NeoForge AttachmentType
    private static List<AttachmentType<?>> attachmentTypes;

    // 路径 B：LHMiscs.MOB.type()
    private static Object lhMiscsMob;
    private static Object lhMobType;
    private static Method typeMethod;
    private static Method getExisting;
    private static Method getOrCreate;
    private static Method isProper;

    // 路径 C：旧版 HOLDER（1.20 / 部分构建）
    private static Object holder;
    private static Method holderIsProper;
    private static Method holderGet;

    // MobTraitCap 成员
    private static Method setTrait;
    private static Method removeTrait;
    private static Method syncToClient;
    private static Field traitsField;
    private static Field pendingField;

    private L2HostilityIntegration() {
    }

    private static boolean isL2Installed() {
        int state = installState;
        if (state != 0) {
            return state == 1;
        }
        synchronized (INIT_LOCK) {
            if (installState != 0) {
                return installState == 1;
            }
            try {
                capClass = Class.forName(CAP_CLASS, false, L2HostilityIntegration.class.getClassLoader());
                for (String name : TRAIT_CLASS_NAMES) {
                    try {
                        traitClass = Class.forName(name, false, L2HostilityIntegration.class.getClassLoader());
                        break;
                    } catch (ClassNotFoundException ignored) {
                    }
                }
                installState = 1;
                return true;
            } catch (Throwable t) {
                installState = 2;
                return false;
            }
        }
    }

    /**
     * 解析能力访问与 Mutator。可重复调用：注册表/静态字段未就绪时不会永久缓存失败。
     */
    private static void ensureAccess() {
        if (accessReady) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (accessReady) {
                return;
            }
            if (capClass == null && !isL2Installed()) {
                return;
            }
            try {
                resolveCapMembers();
                resolveAttachmentTypes();
                resolveLhMiscsAccess();
                resolveHolderAccess();

                accessReady = setTrait != null || removeTrait != null
                        || traitsField != null
                        || attachmentTypes != null && !attachmentTypes.isEmpty()
                        || getExisting != null || getOrCreate != null
                        || holderGet != null;

                if (!accessReady && WARNED_ACCESS.compareAndSet(false, true)) {
                    WenXingTools.LOGGER.warn(
                            "[effect_purge] L2Hostility access unresolved (attachment={}, lhMiscs={}, holder={}, traitsField={}); strip may no-op",
                            attachmentTypes != null && !attachmentTypes.isEmpty(),
                            getExisting != null || getOrCreate != null,
                            holderGet != null,
                            traitsField != null);
                }
            } catch (Throwable t) {
                if (WARNED_ACCESS.compareAndSet(false, true)) {
                    WenXingTools.LOGGER.warn("[effect_purge] L2Hostility access resolve failed: {}", t.toString());
                } else {
                    WenXingTools.LOGGER.debug("[effect_purge] L2Hostility access resolve failed: {}", t.toString());
                }
            }
        }
    }

    private static void resolveCapMembers() {
        if (capClass == null) {
            return;
        }
        setTrait = findSetTrait(capClass);
        removeTrait = findRemoveTrait(capClass);
        syncToClient = findMethod(capClass, "syncToClient", 1);
        if (syncToClient == null) {
            syncToClient = findMethod(capClass, "sync", 1);
        }
        traitsField = findField(capClass, "traits");
        pendingField = findField(capClass, "pending");
    }

    @SuppressWarnings("unchecked")
    private static void resolveAttachmentTypes() {
        if (attachmentTypes != null && !attachmentTypes.isEmpty()) {
            return;
        }
        List<AttachmentType<?>> found = new ArrayList<>();
        for (String id : ATTACHMENT_IDS) {
            AttachmentType<?> type = lookupAttachment(ResourceLocation.parse(id));
            if (type != null && !found.contains(type)) {
                found.add(type);
            }
        }
        // 扫描 l2hostility 命名空间，兼容 AttReg 路径变化
        try {
            for (ResourceLocation id : NeoForgeRegistries.ATTACHMENT_TYPES.keySet()) {
                if (!"l2hostility".equals(id.getNamespace())) {
                    continue;
                }
                AttachmentType<?> type = lookupAttachment(id);
                if (type != null && !found.contains(type)) {
                    found.add(type);
                }
            }
        } catch (Throwable ignored) {
        }
        if (!found.isEmpty()) {
            attachmentTypes = List.copyOf(found);
        }
    }

    private static AttachmentType<?> lookupAttachment(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        try {
            return NeoForgeRegistries.ATTACHMENT_TYPES.get(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void resolveLhMiscsAccess() {
        if (getExisting != null || getOrCreate != null) {
            return;
        }
        try {
            Class<?> miscs = Class.forName(LH_MISCS_CLASS, false, L2HostilityIntegration.class.getClassLoader());
            Field mobField = findField(miscs, "MOB");
            if (mobField == null) {
                return;
            }
            mobField.setAccessible(true);
            lhMiscsMob = mobField.get(null);
            if (lhMiscsMob == null) {
                return;
            }
            // CapVal#type() → getExisting/getOrCreate/isProper
            typeMethod = findMethod(lhMiscsMob.getClass(), "type", 0);
            lhMobType = typeMethod != null ? typeMethod.invoke(lhMiscsMob) : null;
            Object api = lhMobType != null ? lhMobType : lhMiscsMob;
            getExisting = findMethod(api.getClass(), "getExisting", 1);
            getOrCreate = findMethod(api.getClass(), "getOrCreate", 1);
            if (getExisting == null) {
                getExisting = findMethod(api.getClass(), "get", 1);
            }
            isProper = findMethod(api.getClass(), "isProper", 1);
        } catch (Throwable ignored) {
            // 走 HOLDER / Attachment 兜底
        }
    }

    private static void resolveHolderAccess() {
        if (holderGet != null || capClass == null) {
            return;
        }
        try {
            holder = readStaticField(capClass, "HOLDER");
            if (holder == null) {
                return;
            }
            holderIsProper = findMethod(holder.getClass(), "isProper", 1);
            holderGet = findMethod(holder.getClass(), "get", 1);
            if (holderGet == null || holderIsProper == null) {
                Object inner = readInstanceField(holder, "TYPE");
                if (inner == null) {
                    inner = readInstanceField(holder, "holder");
                }
                if (inner != null) {
                    if (holderIsProper == null) {
                        holderIsProper = findMethod(inner.getClass(), "isProper", 1);
                    }
                    if (holderGet == null) {
                        holderGet = findMethod(inner.getClass(), "get", 1);
                    }
                    if (holderGet != null && holderGet.getDeclaringClass() != holder.getClass()) {
                        holder = inner;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object readStaticField(Class<?> clazz, String name) {
        try {
            Field field = findField(clazz, name);
            if (field != null) {
                field.setAccessible(true);
                return field.get(null);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object readInstanceField(Object instance, String name) {
        if (instance == null) {
            return null;
        }
        try {
            Field field = findField(instance.getClass(), name);
            if (field != null) {
                field.setAccessible(true);
                return field.get(instance);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findSetTrait(Class<?> capClass) {
        if (traitClass != null) {
            try {
                Method m = capClass.getMethod("setTrait", traitClass, int.class);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Method method : capClass.getMethods()) {
            String name = method.getName();
            if (!"setTrait".equals(name) && !"setTraitLevel".equals(name)) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 2 && params[1] == int.class) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Method findRemoveTrait(Class<?> capClass) {
        if (traitClass != null) {
            try {
                Method m = capClass.getMethod("removeTrait", traitClass);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Method method : capClass.getMethods()) {
            if (!"removeTrait".equals(method.getName())) {
                continue;
            }
            if (method.getParameterCount() == 1) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
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

    @SuppressWarnings("unchecked")
    private static Object getCap(LivingEntity target) throws Exception {
        // A: NeoForge Attachment（1.21 L2Library 3.x）
        if (attachmentTypes != null) {
            for (AttachmentType<?> type : attachmentTypes) {
                Object cap = target.getExistingDataOrNull((AttachmentType<Object>) type);
                if (cap != null) {
                    return cap;
                }
            }
        }
        // B: LHMiscs.MOB.type().getExisting / getOrCreate
        if (getExisting != null) {
            Object api = lhMobType != null ? lhMobType : lhMiscsMob;
            Object result = getExisting.invoke(api, target);
            if (result instanceof Optional<?> optional) {
                Object cap = optional.orElse(null);
                if (cap != null) {
                    return cap;
                }
            } else if (result != null) {
                return result;
            }
        }
        if (getOrCreate != null) {
            Object api = lhMobType != null ? lhMobType : lhMiscsMob;
            Object cap = getOrCreate.invoke(api, target);
            if (cap != null) {
                return cap;
            }
        }
        // C: 旧版 HOLDER.get
        if (holderGet != null && holder != null) {
            Object cap = holderGet.invoke(holder, target);
            if (cap instanceof Optional<?> optional) {
                return optional.orElse(null);
            }
            return cap;
        }
        return null;
    }

    private static boolean isProper(LivingEntity target) {
        try {
            if (isProper != null) {
                Object api = lhMobType != null ? lhMobType : lhMiscsMob;
                Object result = isProper.invoke(api, target);
                return !(result instanceof Boolean b) || b;
            }
            if (holderIsProper != null && holder != null) {
                Object result = holderIsProper.invoke(holder, target);
                return !(result instanceof Boolean b) || b;
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> traitsMap(Object cap) throws Exception {
        if (cap == null || traitsField == null) {
            return null;
        }
        Object value = traitsField.get(cap);
        return value instanceof Map<?, ?> map ? (Map<Object, Object>) map : null;
    }

    /**
     * 清除目标身上全部 L2Hostility 词条并同步客户端。
     * <p>
     * 对齐 {@code RemoveTraitEnchantment}：{@code removeTrait}（非 ticking 时立即删）+ {@code syncToClient}。
     * 同时清空 {@code traits}/{@code pending}，并摘除 {@code l2hostility:*} 属性修饰
     * （{@code AttributeTrait} / {@code TraitManager.scale} 在 removeTrait 后不会自动还原）。
     */
    public static void strip(LivingEntity target) {
        if (target == null || !isL2Installed()) {
            return;
        }
        ensureAccess();
        try {
            if (!isProper(target)) {
                return;
            }
            Object cap = getCap(target);
            if (cap == null) {
                return;
            }
            Map<Object, Object> traits = traitsMap(cap);
            if (traits == null || traits.isEmpty()) {
                // 空表仍清一次 pending/属性，防止 setTrait(0) 队列残留
                clearPending(cap);
                stripHostilityAttributeModifiers(target);
                return;
            }
            List<Object> keys = new ArrayList<>(traits.keySet());
            // 1) 官方 RemoveTraitEnchantment：立即 removeTrait
            if (removeTrait != null) {
                for (Object trait : keys) {
                    try {
                        removeTrait.invoke(cap, trait);
                    } catch (Throwable ignored) {
                    }
                }
            }
            // 2) 兼容 commandClearTrait 的 setTrait(0)（写入 pending，由 clearPending/tick 落地）
            if (setTrait != null) {
                for (Object trait : keys) {
                    try {
                        setTrait.invoke(cap, trait, 0);
                    } catch (Throwable ignored) {
                    }
                }
            }
            // 3) 丢弃 pending，避免下 tick 把词条写回；并强制清空映射
            clearPending(cap);
            traits.clear();
            // 4) AttributeTrait / hostility_health 在 removeTrait 后仍留在属性上
            stripHostilityAttributeModifiers(target);
            // 5) 同步客户端（头顶词条）
            syncCap(target, cap);
        } catch (Throwable t) {
            if (WARNED_STRIP.compareAndSet(false, true)) {
                WenXingTools.LOGGER.warn("[effect_purge] L2Hostility strip failed: {}", t.toString());
            } else {
                WenXingTools.LOGGER.debug("[effect_purge] L2Hostility strip failed: {}", t.toString());
            }
        }
    }

    private static void clearPending(Object cap) {
        if (pendingField == null || cap == null) {
            return;
        }
        try {
            Object pending = pendingField.get(cap);
            if (pending instanceof List<?> list) {
                list.clear();
            } else if (pending instanceof Collection<?> collection) {
                collection.clear();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void syncCap(LivingEntity target, Object cap) {
        if (syncToClient != null) {
            try {
                syncToClient.invoke(cap, target);
                return;
            } catch (Throwable ignored) {
            }
        }
        // NeoForge 原生 Attachment 同步兜底
        if (attachmentTypes != null) {
            for (AttachmentType<?> type : attachmentTypes) {
                try {
                    target.syncData(type);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static volatile List<net.minecraft.core.Holder<Attribute>> attributeCache;

    /** 摘除 l2hostility 命名空间下的属性修饰（词条属性与等级血量缩放）。 */
    private static void stripHostilityAttributeModifiers(LivingEntity target) {
        try {
            for (net.minecraft.core.Holder<Attribute> attribute : attributeHolders()) {
                AttributeInstance instance = target.getAttribute(attribute);
                if (instance == null || instance.getModifiers().isEmpty()) {
                    continue;
                }
                for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                    ResourceLocation id = modifier.id();
                    if (id != null && "l2hostility".equals(id.getNamespace())) {
                        instance.removeModifier(id);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static List<net.minecraft.core.Holder<Attribute>> attributeHolders() {
        List<net.minecraft.core.Holder<Attribute>> cache = attributeCache;
        if (cache == null) {
            List<net.minecraft.core.Holder<Attribute>> list = new ArrayList<>();
            BuiltInRegistries.ATTRIBUTE.holders().forEach(list::add);
            cache = List.copyOf(list);
            attributeCache = cache;
        }
        return cache;
    }

    /** NBT 层清 hostility/affix 关键字（1.21 L2 走 Attachment，通常无效，仅兜底）。 */
    public static void stripNbtKeys(LivingEntity target) {
        if (target == null) {
            return;
        }
        CompoundTag data = target.getPersistentData();
        for (String key : EffectPurgeSupport.copyKeys(data)) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.contains("hostility") || lower.contains("affix") || lower.contains("affinit")
                    || lower.contains("l2hostility")) {
                data.remove(key);
            }
        }
    }

    /** L2 模组是否在场（不要求能力 API 已解析成功）。 */
    public static boolean isPresent() {
        return isL2Installed();
    }

    /**
     * 目标是否带有 L2 词条。
     * 能力可读时以 {@code traits} 映射为准；读不到但 L2 在场时返回 true，避免探测失败挡住清除。
     */
    public static boolean hasTraits(LivingEntity target) {
        if (target == null || !isL2Installed()) {
            return false;
        }
        ensureAccess();
        try {
            if (!isProper(target)) {
                return false;
            }
            Object cap = getCap(target);
            if (cap == null) {
                // 访问已就绪且无 cap = 无词条；访问未就绪则宁可多清一次
                return !accessReady;
            }
            Map<Object, Object> traits = traitsMap(cap);
            if (traits == null) {
                return true;
            }
            return !traits.isEmpty();
        } catch (Throwable t) {
            return true;
        }
    }

    static Class<?> traitClass() {
        return traitClass;
    }
}
