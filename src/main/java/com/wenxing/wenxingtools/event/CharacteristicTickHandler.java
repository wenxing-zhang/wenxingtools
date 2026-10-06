package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.capability.AuthorityDataProvider;
import com.wenxing.wenxingtools.capability.IAuthorityData;
import com.wenxing.wenxingtools.util.CombatUtil;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CharacteristicTickHandler {
    private CharacteristicTickHandler() {}

    private static final Map<UUID, Boolean> LAST_MAYFLY_STATE = new ConcurrentHashMap<>();

    public static void clearPlayerState(UUID playerId) {
        if (playerId == null) return;
        LAST_MAYFLY_STATE.remove(playerId);
    }


    public static void reapplyFreedomAfterWorldChange(ServerPlayer player) {
        if (player == null) return;
        UUID pid = player.getUUID();
        LAST_MAYFLY_STATE.remove(pid);

        IAuthorityData data = AuthorityDataProvider.getData(player);
        if (data == null) {
            return;
        }
        if (PermissionUtil.isFreedomCharacteristicActiveOrRevoke(player, data, PermissionUtil.hasPermission(player))) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
            LAST_MAYFLY_STATE.put(pid, true);
        }
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
            if (LAST_MAYFLY_STATE.remove(player.getUUID(), Boolean.TRUE)) {
                if (!player.isCreative() && !player.isSpectator()) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                }
                player.onUpdateAbilities();
            }
            return;
        }


        boolean hasPermission = (invincibleOn || freedomOn)
                ? PermissionUtil.hasPermission(sPlayer)
                : false;

        boolean invincibleActive = PermissionUtil.isInvincibleCharacteristicActiveOrRevoke(sPlayer, data, hasPermission);
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

        if (PermissionUtil.isFreedomCharacteristicActiveOrRevoke(sPlayer, data, hasPermission)) {
            boolean desired = true;
            UUID pid = player.getUUID();
            Boolean last = LAST_MAYFLY_STATE.get(pid);
            boolean current = player.getAbilities().mayfly;
            if (!current || last == null || last != desired) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
                LAST_MAYFLY_STATE.put(pid, desired);
            }
        } else {
            UUID pid = player.getUUID();
            if (LAST_MAYFLY_STATE.remove(pid, Boolean.TRUE)) {
                if (!player.isCreative() && !player.isSpectator()) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                }
                player.onUpdateAbilities();
            }
        }
    }
}
