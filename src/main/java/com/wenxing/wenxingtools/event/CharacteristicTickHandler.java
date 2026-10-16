package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.capability.AuthorityDataProvider;
import com.wenxing.wenxingtools.capability.IAuthorityData;
import com.wenxing.wenxingtools.entity.FreedomFlightService;
import com.wenxing.wenxingtools.network.packet.FlightTogglePacket;
import com.wenxing.wenxingtools.util.CombatUtil;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public final class CharacteristicTickHandler {
    private CharacteristicTickHandler() {}

    public static void clearPlayerState(UUID playerId) {
        if (playerId == null) return;
        FreedomFlightService.clearPlayerState(playerId);
        FlightTogglePacket.clearCooldown(playerId);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            ServerPlayer sPlayer = event.player instanceof ServerPlayer sp ? sp : null;
            if (sPlayer == null) {
                return;
            }
            IAuthorityData data = AuthorityDataProvider.getData(sPlayer);
            if (data != null) {
                handleCharacteristicTick(sPlayer, event.player, data);
            }
        }
    }

    private static void handleCharacteristicTick(ServerPlayer sPlayer, Player player, IAuthorityData data) {
        boolean invincibleOn = data.isInvincibleCharacteristicOn();
        boolean freedomOn = data.isFreedomCharacteristicOn();


        if (!invincibleOn && !freedomOn
                && !data.isResourceCharacteristicOn()
                && !data.isKillAuraCharacteristicOn()) {

            if (player.invulnerableTime >= 20) {
                player.invulnerableTime = 0;
            }
            // 未飞行时无须触碰权威数据（stopFlight 内的能力查询对每刻每玩家都是白付）
            if (data.isFreedomFlying()) {
                FreedomFlightService.stopFlight(player);
            }
            return;
        }


        boolean invincibleActive = PermissionUtil.isInvincibleCharacteristicActiveOrRevoke(sPlayer, data);
        if (invincibleActive) {
            player.invulnerableTime = 20;
            if (!player.getAbilities().invulnerable) {
                player.getAbilities().invulnerable = true;
                player.onUpdateAbilities();
            }
            CombatUtil.anchorPlayerHealthByCommand(sPlayer);
        } else if (!data.isInvincibleCharacteristicOn()) {
            if (!player.isCreative() && !player.isSpectator() && player.getAbilities().invulnerable) {
                player.getAbilities().invulnerable = false;
                player.onUpdateAbilities();
            }
            if (player.invulnerableTime >= 20) {
                player.invulnerableTime = 0;
            }
        }

        // 自由飞行：开关关闭或失权自动收起；贴地满 5 刻收起（服务端权威）
        FreedomFlightService.maintainFlight(sPlayer, data);
    }
}
