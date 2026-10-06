package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.capability.AuthorityDataProvider;
import com.wenxing.wenxingtools.capability.IAuthorityData;
import com.wenxing.wenxingtools.util.CombatUtil;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CombatCharacteristicHandlers {
    private CombatCharacteristicHandlers() {}

    private static boolean invincibleFeatureActive(Player player) {
        if (!(player instanceof ServerPlayer sp)) return false;
        IAuthorityData data = AuthorityDataProvider.getData(sp);
        return data != null && PermissionUtil.isInvincibleCharacteristicActiveOrRevoke(sp, data, PermissionUtil.hasPermission(sp));
    }

    private static void runAttackKillChain(Player attacker, LivingEntity target) {
        if (!(attacker instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null) return;
        if (target instanceof Player) return;
        CombatUtil.forceKillEntityByCommandChain(serverPlayer.getServer(), target, serverPlayer);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttackForAttackCharacteristic(LivingAttackEvent event) {

        Player attacker = AmpSupport.resolvePlayerAttacker(event.getSource());
        if (attacker != null && invincibleFeatureActive(attacker)) {
            LivingEntity target = event.getEntity();
            if (target instanceof Player) return;

            if (CombatUtil.isKillChainRunning()) return;
            runAttackKillChain(attacker, target);

            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (invincibleFeatureActive(player)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (invincibleFeatureActive(player)) {
                event.setCanceled(true);
                event.setAmount(0);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (invincibleFeatureActive(player)) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();

        if (target instanceof Player player) {
            if (invincibleFeatureActive(player)) {
                event.setAmount(0.0f);
            }
            return;
        }

        if (!CombatUtil.isKillChainRunning()) {
            return;
        }
        Player attacker = AmpSupport.resolvePlayerAttacker(event.getSource());
        if (attacker != null && invincibleFeatureActive(attacker)) {
            event.setAmount(CombatUtil.DIRECT_KILL_DAMAGE);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        if (hit.getEntity() instanceof Player player && invincibleFeatureActive(player)) {
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        }
    }
}
