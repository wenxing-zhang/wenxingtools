package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.integration.InfernalMobsIntegration;
import com.wenxing.wenxingtools.integration.L2HostilityIntegration;
import com.wenxing.wenxingtools.util.CombatReentry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 净化增幅结算核心。
 *
 * <p><b>持续窗（全部等级）</b>：命中清药水并开 {@code 10×等级} 秒窗口；
 * 窗内每 tick 再清药水，禁止恢复生命，受到的伤害改写为虚空伤害。
 *
 * <p><b>255 级（核清）额外</b>：命中时再清已有属性修饰、L2 词条 / Infernal 词缀。
 *
 * <p>再次命中重置持续窗并再清一次。
 */
public final class EffectPurgeSupport {

    /** 目标 persistent NBT：持续窗截止游戏刻。 */
    public static final String PURGE_UNTIL_TAG = WenXingTools.MODID + ":effect_purge_until";

    /** 核清等级阈值：≥255 在持续窗外再清属性修饰 + L2/Infernal。 */
    public static final int NUCLEAR_LEVEL = 255;

    /** 每级持续秒数（持续窗时长 = 10 × 等级 秒）。 */
    public static final int SECONDS_PER_LEVEL = 10;

    /** Attribute 注册表 Holder 列表缓存。 */
    private static volatile List<Holder<Attribute>> attributeCache;

    private EffectPurgeSupport() {
    }

    /**
     * 命中结算：一律开持续窗（10×等级 秒，禁治疗 + 虚空伤害 + 每 tick 清药水）。
     * level ≥ 255 时额外核清属性修饰与 L2/Infernal 词缀。
     */
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

    /** 只清药水；跳过第三方锁类效果（如 attributearch:wenxing_bind 禁锢）。 */
    public static void suppressEffects(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        clearPurgeableEffects(target);
    }

    private static void clearPurgeableEffects(LivingEntity target) {
        for (MobEffectInstance effect : List.copyOf(target.getActiveEffects())) {
            if (isProtectedFromPurge(effect)) {
                continue;
            }
            target.removeEffect(effect.getEffect());
        }
    }

    private static boolean isProtectedFromPurge(MobEffectInstance effect) {
        if (effect == null) {
            return false;
        }
        ResourceLocation id = effect.getEffect().unwrapKey()
                .map(net.minecraft.resources.ResourceKey::location)
                .orElse(null);
        return id != null
                && "attributearch".equals(id.getNamespace())
                && "wenxing_bind".equals(id.getPath());
    }

    /** 核清：药水 + 全部属性修饰 + L2 词条 + Infernal 词缀（255 级命中时）。 */
    public static void purgeNuclear(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        clearPurgeableEffects(target);
        stripAttributeModifiers(target);
        InfernalMobsIntegration.strip(target);
        // L2：模组在场即尝试清（strip 内部按 Attachment/LHMiscs/HOLDER 解析）
        if (L2HostilityIntegration.isPresent()) {
            L2HostilityIntegration.strip(target);
        }
        L2HostilityIntegration.stripNbtKeys(target);
    }

    /** 当前是否处于持续窗（1–3 与 255 均开启；窗内禁治疗 + 伤害转虚空 + 每 tick 清药水）。 */
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
        return CombatReentry.isVoidConverting();
    }

    public static void setVoidConverting(boolean converting) {
        CombatReentry.setVoidConverting(converting);
    }

    /**
     * 每 tick 维护：过期摘标记；窗内每 tick 清药水。
     * 禁治疗走 {@code LivingHealEvent}，写入拦截走 addEffect/forceAddEffect Mixin，
     * 虚空伤害走 {@code actuallyHurt} Mixin，均不在此路径。
     */
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
        return true;
    }

    /** 清掉目标全部属性修饰（保留基础值）。核清专用。 */
    static void stripAttributeModifiers(LivingEntity target) {
        for (Holder<Attribute> attribute : attributeHolders()) {
            AttributeInstance instance = target.getAttribute(attribute);
            if (instance == null || instance.getModifiers().isEmpty()) {
                continue;
            }
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                ResourceLocation id = modifier.id();
                if (id != null) {
                    instance.removeModifier(id);
                }
            }
        }
    }

    private static List<Holder<Attribute>> attributeHolders() {
        List<Holder<Attribute>> attributes = attributeCache;
        if (attributes == null) {
            List<Holder<Attribute>> list = new ArrayList<>();
            BuiltInRegistries.ATTRIBUTE.holders().forEach(list::add);
            attributes = List.copyOf(list);
            attributeCache = attributes;
        }
        return attributes;
    }

    /** 供测试/调试：读取持续窗截止游戏刻，无标记返回 0。 */
    public static long getSuppressUntil(LivingEntity entity) {
        if (entity == null) {
            return 0L;
        }
        return entity.getPersistentData().getLong(PURGE_UNTIL_TAG);
    }

    /** 供软集成复制键名后删除（避免遍历时修改）。 */
    public static List<String> copyKeys(CompoundTag tag) {
        return List.copyOf(tag.getAllKeys());
    }
}
