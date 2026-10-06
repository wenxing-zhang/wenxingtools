package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.util.CombatUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WenXingTools.MODID)
public final class EchoAmplificationEvents {


    public static final int HITS_PER_LEVEL = 10;

    private static final ThreadLocal<Boolean> ECHO_CHAIN_RUNNING = ThreadLocal.withInitial(() -> false);

    private EchoAmplificationEvents() {
    }

    public static boolean isEchoChainRunning() {
        return ECHO_CHAIN_RUNNING.get();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurtForEchoAmplification(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getAmount() <= 0.0f) {
            return;
        }
        if (CombatUtil.isKillChainRunning() || ECHO_CHAIN_RUNNING.get()) {
            return;
        }

        Player player = AmpSupport.resolvePlayerAttacker(event.getSource());
        if (player == null || CombatUtil.isNotValidAttackerPlayer(player)) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (!target.isAlive()) {
            return;
        }
        if (target instanceof Player) {
            return;
        }
        if (!CombatUtil.tryEnterEchoHit(target)) {
            return;
        }

        int level = resolveEchoAmplificationLevel(player);
        if (level <= 0) {
            return;
        }

        float attackPower = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (!(attackPower > 0.0f) || Float.isNaN(attackPower)) {
            return;
        }

        applyEchoVoidDamage(serverPlayer, target, level, attackPower);
    }

    static int resolveEchoAmplificationLevel(Player player) {
        return AmpSupport.resolveEnchantLevel(player, ModEnchantments.ECHO_AMPLIFICATION.get());
    }


    private static void applyEchoVoidDamage(ServerPlayer player, LivingEntity target, int level, float attackPower) {
        if (ECHO_CHAIN_RUNNING.get()) {
            return;
        }
        int hits = HITS_PER_LEVEL * level;
        if (hits <= 0 || !(attackPower > 0.0f)) {
            return;
        }


        DamageSource voidSource = CombatUtil.createVanillaOutOfWorldSource(player);
        if (voidSource == null) {
            voidSource = player.damageSources().playerAttack(player);
        }

        ECHO_CHAIN_RUNNING.set(true);
        try {
            for (int i = 0; i < hits; i++) {
                if (target.isRemoved() || !target.isAlive()) {
                    break;
                }
                if (target.isDeadOrDying() && target.deathTime > 0) {
                    break;
                }
                target.invulnerableTime = 0;
                target.hurtTime = 0;
                target.hurt(voidSource, attackPower);
            }
        } finally {
            ECHO_CHAIN_RUNNING.set(false);
        }


        if (!target.isRemoved() && target.isAlive()) {
            int purgeLevel = EffectPurgeEvents.resolveEffectPurgeLevel(player);
            if (purgeLevel > 0) {
                EffectPurgeSupport.applyOnHit(target, target.level().getGameTime(), purgeLevel);
            } else if (EffectPurgeSupport.isSuppressed(target)) {
                EffectPurgeSupport.suppressEffects(target);
            }
        }
    }
}
