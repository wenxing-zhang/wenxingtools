package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.compat.SoftModCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 可选模组联动事件：head 栏、金冠 Curios 属性、时间之瓶。
 * 全部走 {@link SoftModCompat}，模组缺失时静默跳过。
 */
public final class CompatGameplayEvents {

    private static int tickCounter = 0;

    private CompatGameplayEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SoftModCompat.ensureHeadSlot(player.server, player);
        SoftModCompat.giveStarterTiabOnce(player);
        SoftModCompat.syncGoldCrownCuriosBonus(player);
    }

    @SubscribeEvent
    public static void onItemCrafted(net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        SoftModCompat.fillTiabTime(event.getCrafting());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tickCounter++;
        if (tickCounter % 20 != 0) {
            return;
        }
        var server = event.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SoftModCompat.refreshPlayerTiab(player);
            SoftModCompat.syncGoldCrownCuriosBonus(player);
        }
    }
}
