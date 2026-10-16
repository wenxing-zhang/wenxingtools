package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.attachment.AuthorityAttachments;
import com.wenxing.wenxingtools.attachment.IAuthorityData;
import com.wenxing.wenxingtools.entity.FlightSeatEntity;
import com.wenxing.wenxingtools.entity.FlightSeatInputStore;
import com.wenxing.wenxingtools.entity.FlightSeatService;
import com.wenxing.wenxingtools.network.ModNetwork;
import com.wenxing.wenxingtools.network.packet.FlightSummonPacket;
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
        FlightSeatInputStore.clear(playerId);
        FlightSummonPacket.clearCooldown(playerId);
    }

    /** 切维 / 重生后回收飞行座残留——载具不跨维度，旧实例不回收会留下无人骑乘的孤儿实体。 */
    public static void reclaimFlightSeatAfterWorldChange(ServerPlayer player) {
        if (player == null) {
            return;
        }
        FlightSeatService.dismountAndDiscard(player);
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
            reclaimSeatIfRiding(player);
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
                    true);
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

        // 开关关闭或失权自动关：回收飞行座（载具自身 tick 也会判一次，此处兜住未 tick 的情形）
        if (!PermissionUtil.isFreedomCharacteristicActiveOrRevoke(sPlayer, data, hasPermission)) {
            reclaimSeatIfRiding(player);
        }
    }

    private static void reclaimSeatIfRiding(Player player) {
        if (player.getVehicle() instanceof FlightSeatEntity) {
            FlightSeatService.dismountAndDiscard(player);
        }
    }
}
