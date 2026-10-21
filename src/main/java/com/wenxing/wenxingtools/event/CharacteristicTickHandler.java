package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.attachment.AuthorityAttachments;
import com.wenxing.wenxingtools.attachment.IAuthorityData;
import com.wenxing.wenxingtools.entity.FreedomFlightService;
import com.wenxing.wenxingtools.network.ModNetwork;
import com.wenxing.wenxingtools.network.packet.FlightTogglePacket;
import com.wenxing.wenxingtools.util.CombatUtil;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.UUID;

@EventBusSubscriber(modid = WenXingTools.MODID)
public final class CharacteristicTickHandler {
    private CharacteristicTickHandler() {}

    public static void clearPlayerState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        FreedomFlightService.clearPlayerState(playerId);
        FlightTogglePacket.clearCooldown(playerId);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        if (!(player instanceof ServerPlayer sPlayer)) {
            return;
        }
        IAuthorityData data = AuthorityAttachments.getData(sPlayer);
        if (data != null) {
            handleCharacteristicTick(sPlayer, player, data);
        }
    }

    private static void handleCharacteristicTick(ServerPlayer sPlayer, Player player, IAuthorityData data) {
        boolean invincibleOn = data.isInvincibleCharacteristicOn();
        boolean freedomOn = data.isFreedomCharacteristicOn();
        boolean resourceOn = data.isResourceCharacteristicOn();
        boolean killAuraOn = data.isKillAuraCharacteristicOn();

        if (!invincibleOn && !freedomOn && !resourceOn && !killAuraOn) {
            if (player.invulnerableTime > 20) {
                player.invulnerableTime = 0;
            }
            // 未飞行时无须触碰权威数据（stopFlight 内的同步对每刻每玩家都是白付）
            if (data.isFreedomFlying()) {
                FreedomFlightService.stopFlight(player);
            }
            return;
        }

        boolean hasPermission = PermissionUtil.hasPermission(sPlayer);
        boolean revoked = false;

        if (invincibleOn && !hasPermission) {
            data.setInvincibleCharacteristic(false);
            revoked = true;
        }
        if (resourceOn && !hasPermission) {
            data.setResourceCharacteristic(false);
            revoked = true;
        }
        if (freedomOn && !hasPermission) {
            data.setFreedomCharacteristic(false);
            revoked = true;
        }
        if (killAuraOn && !hasPermission) {
            data.setKillAuraCharacteristic(false);
            revoked = true;
        }
        if (revoked) {
            sPlayer.displayClientMessage(
                    Component.translatable("msg.wenxingtools.whitelist.revoked").withStyle(ChatFormatting.RED),
                    false);
            ModNetwork.syncAuthorityData(sPlayer);
        }

        if (PermissionUtil.isInvincibleCharacteristicActiveOrRevoke(sPlayer, data, hasPermission)) {
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
            if (player.invulnerableTime > 20) {
                player.invulnerableTime = 0;
            }
        }

        // 自由飞行：开关关闭或失权自动收起；贴地满 5 刻收起（服务端权威）
        FreedomFlightService.maintainFlight(sPlayer, data);
    }
}
