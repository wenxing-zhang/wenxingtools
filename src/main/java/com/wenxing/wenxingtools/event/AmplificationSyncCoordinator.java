package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

/**
 * 装备变化时统一刷新各增幅附魔缓存。
 * <p>
 * 攻击增幅不再缓存：等级每次命中现查装备槽（装备变更事件拦不住就地改组件与第三方授予路径），
 * 因此这里只维护生命增幅的缓存与旧存档遗留开关的清理。
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
        LifeAmplificationHandler.syncLifeAmplification(player);
    }

    public static void syncPlayerAmplifications(Player player) {
        LifeAmplificationHandler.syncLifeAmplification(player);
    }

    public static void reinitializePlayerAmplifications(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        // 资源增幅开关已迁到物品组件；旧存档的玩家级 tag 在重初始化时清掉，避免误读
        ResourceAmplificationHandler.clearLegacyPlayerSwitchTag(player);
        LifeAmplificationHandler.clearLifeAmplificationLevelState(player.getUUID());
        PotionAmplificationHandler.clearPotionAmplificationPlayerState(player.getUUID());
        syncPlayerAmplifications(player);
        PotionAmplificationHandler.cleanseHarmfulEffectsIfWorn(player);
        // 切维/重生/登录后客户端 LocalPlayer 重建，强制重发夜视/幸运，避免 addEffect 无变更不发包
        LifeAmplificationHandler.forceResyncLifeAmplificationEffects(player);
    }
}
