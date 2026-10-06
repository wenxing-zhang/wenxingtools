package com.wenxing.wenxingtools.network.packet;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.Consumer;

/**
 * playToClient：仅客户端处理。公共类不得引用 client 包类型，
 * 客户端处理器由 {@code Dist.CLIENT} 初始化阶段注入，避免专用服务器类加载失败。
 */
public record AuthorityDataSyncPacket(CompoundTag dataTag) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AuthorityDataSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WenXingTools.MODID, "authority_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuthorityDataSyncPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, AuthorityDataSyncPacket::dataTag, AuthorityDataSyncPacket::new);

    private static volatile Consumer<CompoundTag> clientSyncHandler = tag -> {};

    public static void setClientSyncHandler(Consumer<CompoundTag> handler) {
        clientSyncHandler = handler != null ? handler : tag -> {};
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AuthorityDataSyncPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> clientSyncHandler.accept(msg.dataTag()));
    }
}
