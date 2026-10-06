package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

/**
 * 装备变化时统一刷新各增幅附魔缓存。
 */
@EventBusSubscriber(modid = WenXingTools.MODID)
public final class AmplificationSyncCoordinator {
    private AmplificationSyncCoordinator() {}

    @SubscribeEvent
    public static void onLivingEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        AttackAmplificationHandler.syncAttackAmplification(player);
        LifeAmplificationHandler.syncLifeAmplification(player);
    }

    public static void syncPlayerAmplifications(Player player) {
        AttackAmplificationHandler.syncAttackAmplification(player);
        LifeAmplificationHandler.syncLifeAmplification(player);
    }

    public static void reinitializePlayerAmplifications(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        LifeAmplificationHandler.clearLifeAmplificationLevelState(player.getUUID());
        PotionAmplificationHandler.clearPotionAmplificationPlayerState(player.getUUID());
        syncPlayerAmplifications(player);
        // 切维/重生/登录后客户端 LocalPlayer 重建，强制重发夜视/幸运，避免 addEffect 无变更不发包
        LifeAmplificationHandler.forceResyncLifeAmplificationEffects(player);
    }
}
