package com.wenxing.wenxingtools.network.packet;

import com.wenxing.wenxingtools.entity.FreedomFlightService;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** 客户端双击跳跃触发的自由飞行开关（起飞 / 收起）。 */
public class FlightTogglePacket {

    private static final long COOLDOWN_MS = 250L;
    private static final Map<UUID, Long> LAST_TOGGLE_MS = new ConcurrentHashMap<>();

    public FlightTogglePacket() {
    }

    public static void clearCooldown(UUID playerId) {
        if (playerId != null) {
            LAST_TOGGLE_MS.remove(playerId);
        }
    }

    public static void encode(FlightTogglePacket msg, FriendlyByteBuf buf) {
    }

    public static FlightTogglePacket decode(FriendlyByteBuf buf) {
        return new FlightTogglePacket();
    }

    public static void handle(FlightTogglePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        if (ctx.getDirection() != NetworkDirection.PLAY_TO_SERVER) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !PermissionUtil.hasPermission(player)) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = LAST_TOGGLE_MS.get(player.getUUID());
            if (last != null && now - last < COOLDOWN_MS) {
                return;
            }
            LAST_TOGGLE_MS.put(player.getUUID(), now);
            FreedomFlightService.toggle(player);
        });
        ctx.setPacketHandled(true);
    }
}
