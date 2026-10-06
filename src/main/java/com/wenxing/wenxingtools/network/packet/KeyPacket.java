package com.wenxing.wenxingtools.network.packet;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.attachment.AuthorityAttachments;
import com.wenxing.wenxingtools.attachment.IAuthorityData;
import com.wenxing.wenxingtools.network.ModNetwork;
import com.wenxing.wenxingtools.util.ModEnums;
import com.wenxing.wenxingtools.util.ModEnums.CharacteristicKey;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record KeyPacket(int key) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KeyPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WenXingTools.MODID, "key"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KeyPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, KeyPacket::key, KeyPacket::new);

    private static final long KEY_COOLDOWN_MS = 200L;
    private static final Map<UUID, Long> LAST_KEY_PRESS = new ConcurrentHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @EventBusSubscriber(modid = WenXingTools.MODID)
    public static class Cleanup {
        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() != null) {
                LAST_KEY_PRESS.remove(event.getEntity().getUUID());
            }
        }
    }

    public static void handle(KeyPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = LAST_KEY_PRESS.get(player.getUUID());
            if (last != null && now - last < KEY_COOLDOWN_MS) {
                return;
            }

            CharacteristicKey target = CharacteristicKey.fromNetworkId(msg.key());
            if (target == null) {
                return;
            }

            // 资源增幅附魔开关：不依赖白名单/OP，只要主手有附魔即可切换
            if (target == CharacteristicKey.RESOURCE_AMP) {
                if (com.wenxing.wenxingtools.event.ResourceAmplificationHandler
                        .tryToggleResourceAmplificationByPlayer(player, player.getMainHandItem())) {
                    LAST_KEY_PRESS.put(player.getUUID(), now);
                }
                return;
            }

            if (!PermissionUtil.hasPermission(player)) {
                return;
            }
            LAST_KEY_PRESS.put(player.getUUID(), now);

            IAuthorityData data = AuthorityAttachments.getData(player);
            if (data == null) {
                return;
            }

            boolean newState;
            Component characteristicName;
            switch (target) {
                case INVINCIBLE -> {
                    newState = !data.isInvincibleCharacteristicOn();
                    data.setInvincibleCharacteristic(newState);
                    if (newState) {
                        data.setLockedHealth(player.getHealth());
                    }
                    characteristicName = Component.translatable(newState
                            ? "msg.wenxingtools.invincible.on"
                            : "msg.wenxingtools.invincible.off");
                }
                case RESOURCE -> {
                    newState = !data.isResourceCharacteristicOn();
                    data.setResourceCharacteristic(newState);
                    characteristicName = newState
                            ? Component.translatable("msg.wenxingtools.resource.on", data.getResourceMultiplier())
                            : Component.translatable("msg.wenxingtools.resource.off");
                }
                case FREEDOM -> {
                    newState = !data.isFreedomCharacteristicOn();
                    data.setFreedomCharacteristic(newState);
                    if (newState) {
                        player.getAbilities().mayfly = true;
                        characteristicName = Component.translatable("msg.wenxingtools.freedom.on");
                    } else {
                        if (!player.isCreative() && !player.isSpectator()) {
                            player.getAbilities().mayfly = false;
                            player.getAbilities().flying = false;
                        }
                        characteristicName = Component.translatable("msg.wenxingtools.freedom.off");
                    }
                    player.onUpdateAbilities();
                }
                case KILL_AURA -> {
                    newState = !data.isKillAuraCharacteristicOn();
                    data.setKillAuraCharacteristic(newState);
                    Component modeText = Component.translatable(
                            ModEnums.KillAuraMode.fromPersistentId(data.getKillAuraMode()).translationKey());
                    characteristicName = newState
                            ? Component.translatable("msg.wenxingtools.kill_aura.on", modeText)
                            : Component.translatable("msg.wenxingtools.kill_aura.off");
                }
                default -> {
                    return;
                }
            }

            ChatFormatting color = newState ? ChatFormatting.GREEN : ChatFormatting.RED;
            player.displayClientMessage(characteristicName.copy().withStyle(color), true);
            ModNetwork.syncAuthorityData(player);
        });
    }
}
