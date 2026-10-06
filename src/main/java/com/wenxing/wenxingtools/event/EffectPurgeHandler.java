package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.util.CombatReentry;
import com.wenxing.wenxingtools.util.CombatUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 净化增幅事件层。
 *
 * <p><b>持续窗（各等级）</b>：Attack 阶段即开窗（不等 Hurt/LivingDamage）；
 * 窗内每 tick 清药水、禁止恢复生命、受到的伤害改写为虚空伤害
 * （{@link com.wenxing.wenxingtools.mixin.LivingEntityPurgeDamageMixin}）；
 * 药水写入由 {@link com.wenxing.wenxingtools.mixin.LivingEntityPurgeEffectMixin} 直接拦 addEffect/forceAddEffect。
 *
 * <p><b>255 级额外</b>：命中核清属性修饰 + L2/Infernal 词缀。
 *
 * <p>触发沿用攻击增幅的攻击者解析；对玩家目标不生效。
 */
@EventBusSubscriber(modid = WenXingTools.MODID)
public final class EffectPurgeHandler {

    private EffectPurgeHandler() {
    }

    /**
     * Attack 阶段开净化，不等 Hurt。
     * NeoForge 21.1 无 {@code LivingAttackEvent}；最早伤害事件
     * {@code LivingIncomingDamageEvent} 为 Attack 阶段等价物（早于 LivingDamageEvent/Hurt）。
     * 用 HIGHEST 尽量先于其他入站处理。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingIncomingForEffectPurge(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getAmount() <= 0.0f) {
            return;
        }
        // 杀戮/攻击增幅补刀链/回响链/虚空改写重入不重复净化（仅主刀触发）
        if (CombatUtil.isKillChainRunning()
                || EchoAmplificationHandler.isEchoChainRunning()
                || CombatReentry.isAttackAmpRunning()
                || EffectPurgeSupport.isVoidConverting()) {
            return;
        }

        Player player = CombatUtil.resolvePlayerAttacker(event.getSource());
        if (player == null || CombatUtil.isNotValidAttackerPlayer(player)) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (!target.isAlive()) {
            return;
        }
        // 与攻击增幅一致：不对玩家目标生效（PvP 语义保留）
        if (target instanceof Player) {
            return;
        }
        int level = resolveEffectPurgeLevel(player);
        if (level <= 0) {
            return;
        }

        EffectPurgeSupport.applyOnHit(target, target.level().getGameTime(), level);
    }

    /** 装备上净化增幅总等级（主手优先，回退全槽）。 */
    public static int resolveEffectPurgeLevel(Player player) {
        return EnchantUtil.resolveEnchantLevel(ModEnchantments.EFFECT_PURGE, player);
    }

    /**
     * 持续窗内拒绝效果「适用」判定。
     * 主拦截点为 Mixin（addEffect/forceAddEffect）；此事件覆盖仍走 Applicable 的路径。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (EffectPurgeSupport.isSuppressed(event.getEntity())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /**
     * Added 结束后全量再清（LOWEST：等其他 Added 处理完再统一清空）。
     * 覆盖旁路 Mixin/Applicable 后仍写入的残留。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMobEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!EffectPurgeSupport.isSuppressed(event.getEntity())) {
            return;
        }
        EffectPurgeSupport.suppressEffects(event.getEntity());
    }

    /** 持续窗内禁止恢复生命（自然再生 / 药水治疗 / 吸血 heal 等）。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (EffectPurgeSupport.isSuppressed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** 持续窗维护：过期摘标记；窗内每 tick 清药水。 */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        EffectPurgeSupport.tickSuppression(living);
    }

    /**
     * 构建净化虚空伤害源：原攻击者为玩家则保留归属，否则无实体虚空。
     * 供 {@link com.wenxing.wenxingtools.mixin.LivingEntityPurgeDamageMixin} 调用。
     */
    public static DamageSource resolvePurgeVoidSource(DamageSource original, LivingEntity target) {
        Entity attacker = original != null && original.getEntity() != null
                ? original.getEntity()
                : (original != null ? original.getDirectEntity() : null);
        if (attacker instanceof ServerPlayer serverPlayer) {
            DamageSource credited = CombatUtil.createVanillaOutOfWorldSource(serverPlayer);
            if (credited != null) {
                return credited;
            }
        }
        DamageSource plain = CombatUtil.createVanillaOutOfWorldSource(target.level());
        return plain != null ? plain : original;
    }
}
