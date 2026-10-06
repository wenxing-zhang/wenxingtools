package com.wenxing.wenxingtools.network;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.attachment.AuthorityAttachments;
import com.wenxing.wenxingtools.attachment.IAuthorityData;
import com.wenxing.wenxingtools.network.packet.AuthorityDataSyncPacket;
import com.wenxing.wenxingtools.network.packet.KeyPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = WenXingTools.MODID)
public final class ModNetwork {
    private ModNetwork() {}

    public static final String PROTOCOL_VERSION = "2";

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(KeyPacket.TYPE, KeyPacket.STREAM_CODEC, KeyPacket::handle);
        registrar.playToClient(AuthorityDataSyncPacket.TYPE, AuthorityDataSyncPacket.STREAM_CODEC, AuthorityDataSyncPacket::handle);
    }

    public static void syncAuthorityData(ServerPlayer player) {
        if (player == null) {
            return;
        }
        IAuthorityData data = AuthorityAttachments.getData(player);
        if (data != null) {
            PacketDistributor.sendToPlayer(player, new AuthorityDataSyncPacket(data.serializeNBT()));
        }
    }

    public static void sendKeyToServer(int networkId) {
        PacketDistributor.sendToServer(new KeyPacket(networkId));
    }
}
