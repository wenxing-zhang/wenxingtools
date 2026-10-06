package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.attachment.AuthorityAttachments;
import com.wenxing.wenxingtools.attachment.IAuthorityData;
import com.wenxing.wenxingtools.util.CombatReentry;
import com.wenxing.wenxingtools.util.CombatUtil;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = WenXingTools.MODID)
public final class CombatCharacteristicHandlers {
    private CombatCharacteristicHandlers() {}

    private static boolean invincibleFeatureActive(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }
        IAuthorityData data = AuthorityAttachments.getData(sp);
        return data != null
                && PermissionUtil.isInvincibleCharacteristicActiveOrRevoke(sp, data, PermissionUtil.hasPermission(sp));
    }

    private static void runAttackKillChain(Player attacker, LivingEntity target) {
        if (!(attacker instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null) {
            return;
        }
        if (target instanceof Player) {
            return;
        }
        CombatUtil.forceKillEntityByCommandChain(serverPlayer.getServer(), target, serverPlayer);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingIncomingForAttackCharacteristic(LivingIncomingDamageEvent event) {
        if (CombatUtil.isKillChainRunning()
                || CombatReentry.isEchoChainRunning()
                || CombatReentry.isAttackAmpRunning()) {
            return;
        }
        Player attacker = CombatUtil.resolvePlayerAttacker(event.getSource());
        if (attacker != null && invincibleFeatureActive(attacker)) {
            LivingEntity target = event.getEntity();
            if (target instanceof Player) {
                return;
            }
            runAttackKillChain(attacker, target);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingIncoming(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && invincibleFeatureActive(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof Player player && invincibleFeatureActive(player)) {
            event.setNewDamage(0);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && invincibleFeatureActive(player)) {
            event.setCanceled(true);
            player.setHealth(CombatUtil.getMaxHealth(player));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamagePreForAttacker(LivingDamageEvent.Pre event) {
        if (CombatUtil.isKillChainRunning()
                || CombatReentry.isEchoChainRunning()
                || CombatReentry.isAttackAmpRunning()) {
            return;
        }
        Player attacker = CombatUtil.resolvePlayerAttacker(event.getSource());
        if (attacker != null && invincibleFeatureActive(attacker)) {
            if (event.getEntity() instanceof Player) {
                return;
            }
            event.setNewDamage(CombatUtil.DIRECT_KILL_DAMAGE);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        if (hit.getEntity() instanceof Player player && invincibleFeatureActive(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity aboutToTarget = event.getNewAboutToBeSetTarget();
        if (!(aboutToTarget instanceof ServerPlayer player)) {
            return;
        }
        if (!invincibleFeatureActive(player)) {
            return;
        }
        if (event.getTargetType() == LivingChangeTargetEvent.LivingTargetType.BEHAVIOR_TARGET) {
            event.setCanceled(true);
        }
    }
}
