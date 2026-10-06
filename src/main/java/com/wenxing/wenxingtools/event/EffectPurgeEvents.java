package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.util.CombatUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WenXingTools.MODID)
public final class EffectPurgeEvents {

    private EffectPurgeEvents() {
    }


    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttackForEffectPurge(LivingAttackEvent event) {
        tryApplyEffectPurge(event.getEntity(), event.getSource());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurtForEffectPurge(LivingHurtEvent event) {
        if (event.getAmount() <= 0.0f) {
            return;
        }
        tryApplyEffectPurge(event.getEntity(), event.getSource());
    }

    private static void tryApplyEffectPurge(LivingEntity target, DamageSource source) {
        if (target == null || target.level().isClientSide) {
            return;
        }

        if (CombatUtil.isKillChainRunning()
                || EchoAmplificationEvents.isEchoChainRunning()
                || EffectPurgeSupport.isVoidConverting()
                || AttackAmplificationEvents.isAttackAmpRunning()) {
            return;
        }

        Player player = AmpSupport.resolvePlayerAttacker(source);
        if (player == null || CombatUtil.isNotValidAttackerPlayer(player)) {
            return;
        }

        if (!target.isAlive()) {
            return;
        }

        if (target instanceof Player) {
            return;
        }
        int level = resolveEffectPurgeLevel(player);
        if (level <= 0) {
            return;
        }

        EffectPurgeSupport.applyOnHit(target, target.level().getGameTime(), level);
    }


    public static int resolveEffectPurgeLevel(Player player) {
        return AmpSupport.resolveEnchantLevel(player, ModEnchantments.EFFECT_PURGE.get());
    }


    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (EffectPurgeSupport.isSuppressed(event.getEntity())) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }


    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMobEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (!EffectPurgeSupport.isSuppressed(target)) {
            return;
        }
        EffectPurgeSupport.suppressEffects(target);
    }


    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (EffectPurgeSupport.isSuppressed(event.getEntity())) {
            event.setCanceled(true);
        }
    }


    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        EffectPurgeSupport.tickSuppression(event.getEntity());
    }


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
