package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.capability.AuthorityDataProvider;
import com.wenxing.wenxingtools.capability.IAuthorityData;
import com.wenxing.wenxingtools.entity.FlightSeatEntity;
import com.wenxing.wenxingtools.entity.FlightSeatInputStore;
import com.wenxing.wenxingtools.entity.FlightSeatService;
import com.wenxing.wenxingtools.network.packet.FlightSummonPacket;
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
        FlightSeatInputStore.clear(playerId);
        FlightSummonPacket.clearCooldown(playerId);
    }

    /** 切维 / 重生后回收飞行座残留——载具不跨维度，旧实例不回收会留下无人骑乘的孤儿实体。 */
    public static void reclaimFlightSeatAfterWorldChange(ServerPlayer player) {
        if (player == null) return;
        FlightSeatService.dismountAndDiscard(player);
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
            reclaimSeatIfRiding(player);
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

        // 开关关闭或失权自动关：回收飞行座（载具自身 tick 也会判一次，此处兜住未 tick 的情形）
        if (!PermissionUtil.isFreedomCharacteristicActiveOrRevoke(sPlayer, data)) {
            reclaimSeatIfRiding(player);
        }
    }

    private static void reclaimSeatIfRiding(Player player) {
        if (player.getVehicle() instanceof FlightSeatEntity) {
            FlightSeatService.dismountAndDiscard(player);
        }
    }
}
