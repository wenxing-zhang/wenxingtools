package com.wenxing.wenxingtools.network.packet;

import com.wenxing.wenxingtools.event.EnchantmentEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ResourceAmpTogglePacket {
    private static final long COOLDOWN_MS = 250L;
    private static final Map<UUID, Long> LAST_HANDLE_MS = new ConcurrentHashMap<>();

    public static void clearCooldown(UUID playerId) {
        if (playerId != null) {
            LAST_HANDLE_MS.remove(playerId);
        }
    }

    public ResourceAmpTogglePacket() {
    }

    public static void encode(ResourceAmpTogglePacket msg, FriendlyByteBuf buf) {
    }

    public static ResourceAmpTogglePacket decode(FriendlyByteBuf buf) {
        return new ResourceAmpTogglePacket();
    }

    public static void handle(ResourceAmpTogglePacket msg, Supplier<NetworkEvent.Context> ctx) {
        if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_SERVER) {
            ctx.get().setPacketHandled(true);
            return;
        }
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            long now = System.currentTimeMillis();
            Long last = LAST_HANDLE_MS.get(player.getUUID());
            if (last != null && now - last < COOLDOWN_MS) {
                return;
            }
            LAST_HANDLE_MS.put(player.getUUID(), now);
            EnchantmentEvents.tryToggleResourceAmplificationByPlayer(player, player.getMainHandItem());
        });
        ctx.get().setPacketHandled(true);
    }
}
