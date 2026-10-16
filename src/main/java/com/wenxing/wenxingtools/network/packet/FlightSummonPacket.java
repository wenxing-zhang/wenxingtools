package com.wenxing.wenxingtools.network.packet;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.entity.FlightSeatService;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 客户端双击跳跃触发的飞行座开关（召唤 / 收起）。 */
public record FlightSummonPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FlightSummonPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WenXingTools.MODID, "flight_summon"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlightSummonPacket> STREAM_CODEC =
            StreamCodec.unit(new FlightSummonPacket());

    private static final long COOLDOWN_MS = 250L;
    private static final Map<UUID, Long> LAST_TOGGLE_MS = new ConcurrentHashMap<>();

    public static void clearCooldown(UUID playerId) {
        if (playerId != null) {
            LAST_TOGGLE_MS.remove(playerId);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FlightSummonPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!PermissionUtil.hasPermission(player)) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = LAST_TOGGLE_MS.get(player.getUUID());
            if (last != null && now - last < COOLDOWN_MS) {
                return;
            }
            LAST_TOGGLE_MS.put(player.getUUID(), now);
            FlightSeatService.toggle(player);
        });
    }
}
