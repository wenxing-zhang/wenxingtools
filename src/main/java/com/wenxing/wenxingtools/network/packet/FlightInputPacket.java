package com.wenxing.wenxingtools.network.packet;

import com.wenxing.wenxingtools.entity.FlightSeatEntity;
import com.wenxing.wenxingtools.entity.FlightSeatInputStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端每刻上报的飞行座输入（前后 / 升降）。 */
public class FlightInputPacket {
    private final int forward;
    private final int up;

    public FlightInputPacket(int forward, int up) {
        this.forward = forward;
        this.up = up;
    }

    public static void encode(FlightInputPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.forward);
        buf.writeByte(msg.up);
    }

    public static FlightInputPacket decode(FriendlyByteBuf buf) {
        return new FlightInputPacket(buf.readByte(), buf.readByte());
    }

    public static void handle(FlightInputPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        if (ctx.getDirection() != NetworkDirection.PLAY_TO_SERVER) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            // 只在确实骑在飞行座上时接受输入，避免伪造包驱动其它载具
            if (!(player.getVehicle() instanceof FlightSeatEntity)) {
                return;
            }
            FlightSeatInputStore.set(player.getUUID(), msg.forward, msg.up);
        });
        ctx.setPacketHandled(true);
    }
}
