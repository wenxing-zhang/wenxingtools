package com.wenxing.wenxingtools.network.packet;

import com.wenxing.wenxingtools.capability.AuthorityDataProvider;
import com.wenxing.wenxingtools.capability.IAuthorityData;
import com.wenxing.wenxingtools.network.PacketHandler;
import com.wenxing.wenxingtools.util.ModEnums;
import com.wenxing.wenxingtools.util.ModEnums.CharacteristicKey;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class KeyPacket {

    private static final long TOGGLE_COOLDOWN_MS = 250L;
    private static final Map<UUID, Long> LAST_TOGGLE_MS = new ConcurrentHashMap<>();


    public static void clearCooldown(UUID playerId) {
        if (playerId != null) {
            LAST_TOGGLE_MS.remove(playerId);
        }
    }

    private final int key;

    public KeyPacket(int key) {
        this.key = key;
    }

    public static void encode(KeyPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.key);
    }

    public static KeyPacket decode(FriendlyByteBuf buf) {
        return new KeyPacket(buf.readInt());
    }

    public static void handle(KeyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_SERVER) {
            ctx.get().setPacketHandled(true);
            return;
        }
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            if (!PermissionUtil.hasPermission(player)) {
                return;
            }

            long now = System.currentTimeMillis();
            Long last = LAST_TOGGLE_MS.get(player.getUUID());
            if (last != null && now - last < TOGGLE_COOLDOWN_MS) {
                return;
            }
            LAST_TOGGLE_MS.put(player.getUUID(), now);

            CharacteristicKey target = CharacteristicKey.fromNetworkId(msg.key);
            if (target == null) {
                return;
            }

            IAuthorityData data = AuthorityDataProvider.getData(player);
            if (data == null) {
                player.displayClientMessage(Component.literal("特性数据不可用，请重新登录后再试。").withStyle(ChatFormatting.RED), true);
                return;
            }

            boolean newState;
            String characteristicName;

            switch (target) {
                case INVINCIBLE:
                    newState = !data.isInvincibleCharacteristicOn();
                    data.setInvincibleCharacteristic(newState);
                    if (newState) {
                        data.setLockedHealth(player.getHealth());
                    }
                    characteristicName = newState
                            ? "无敌特性：已开启"
                            : "无敌特性：已关闭";
                    break;
                case RESOURCE:
                    newState = !data.isResourceCharacteristicOn();
                    data.setResourceCharacteristic(newState);
                    characteristicName = newState
                            ? "资源特性：已开启（倍率: " + data.getResourceMultiplier() + "）"
                            : "资源特性：已关闭";
                    break;
                case FREEDOM:
                    newState = !data.isFreedomCharacteristicOn();
                    data.setFreedomCharacteristic(newState);
                    if (newState) {
                        player.getAbilities().mayfly = true;
                        characteristicName = "自由特性：已开启";
                    } else {
                        if (!player.isCreative() && !player.isSpectator()) {
                            player.getAbilities().mayfly = false;
                            player.getAbilities().flying = false;
                            characteristicName = "自由特性：已关闭";
                        } else {
                            characteristicName = "自由特性：已关闭";
                        }
                    }
                    player.onUpdateAbilities();
                    break;
                case KILL_AURA:
                    newState = !data.isKillAuraCharacteristicOn();
                    data.setKillAuraCharacteristic(newState);
                    String modeText = ModEnums.KillAuraMode.fromPersistentId(data.getKillAuraMode()).displayName();
                    characteristicName = newState
                            ? "杀戮光环特性：已开启（当前模式: " + modeText + "）"
                            : "杀戮光环特性：已关闭";
                    break;
                default:
                    return;
            }

            ChatFormatting color = newState ? ChatFormatting.GREEN : ChatFormatting.RED;
            player.displayClientMessage(Component.literal(characteristicName).withStyle(color), true);
            PacketHandler.syncAuthorityData(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
