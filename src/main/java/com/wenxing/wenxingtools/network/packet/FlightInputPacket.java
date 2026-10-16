package com.wenxing.wenxingtools.network.packet;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.entity.FlightSeatEntity;
import com.wenxing.wenxingtools.entity.FlightSeatInputStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端每刻上报的飞行座输入（前后 / 升降）。 */
public record FlightInputPacket(int forward, int up) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FlightInputPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WenXingTools.MODID, "flight_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlightInputPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FlightInputPacket::forward,
                    ByteBufCodecs.VAR_INT, FlightInputPacket::up,
                    FlightInputPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FlightInputPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // 只在确实骑在飞行座上时接受输入，避免伪造包驱动其它载具
            if (!(player.getVehicle() instanceof FlightSeatEntity)) {
                return;
            }
            FlightSeatInputStore.set(player.getUUID(), msg.forward(), msg.up());
        });
    }
}
