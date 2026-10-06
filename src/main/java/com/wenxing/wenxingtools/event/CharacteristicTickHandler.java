package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.attachment.AuthorityAttachments;
import com.wenxing.wenxingtools.attachment.IAuthorityData;
import com.wenxing.wenxingtools.network.ModNetwork;
import com.wenxing.wenxingtools.util.CombatUtil;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = WenXingTools.MODID)
public final class CharacteristicTickHandler {
    private CharacteristicTickHandler() {}

    private static final Map<UUID, Boolean> LAST_MAYFLY_STATE = new ConcurrentHashMap<>();

    public static void clearPlayerState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        LAST_MAYFLY_STATE.remove(playerId);
    }

    /**
     * 切维 / 重生后强制重刷自由飞行能力（对齐 1.20.1）。
     * 客户端 LocalPlayer 会被重建，若服务端 mayfly + 缓存仍为 true 就不会再 onUpdateAbilities，
     * 导致客户端 mayfly 失同步、双击空格无法起飞。
     */
    public static void reapplyFreedomAfterWorldChange(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID pid = player.getUUID();
        LAST_MAYFLY_STATE.remove(pid);

        IAuthorityData data = AuthorityAttachments.getData(player);
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
        UUID pid = player.getUUID();

        if (!invincibleOn && !freedomOn && !resourceOn && !killAuraOn) {
            if (player.invulnerableTime > 20) {
                player.invulnerableTime = 0;
            }
            if (LAST_MAYFLY_STATE.remove(pid, Boolean.TRUE)) {
                if (!player.isCreative() && !player.isSpectator()) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                }
                player.onUpdateAbilities();
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

        if (PermissionUtil.isFreedomCharacteristicActiveOrRevoke(sPlayer, data, hasPermission)) {
            Boolean last = LAST_MAYFLY_STATE.get(pid);
            boolean current = player.getAbilities().mayfly;
            if (!current || last == null || last != Boolean.TRUE) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
                LAST_MAYFLY_STATE.put(pid, Boolean.TRUE);
            }
        } else {
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
