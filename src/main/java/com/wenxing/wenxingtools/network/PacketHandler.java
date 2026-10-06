package com.wenxing.wenxingtools.network;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.capability.AuthorityDataProvider;
import com.wenxing.wenxingtools.capability.IAuthorityData;
import com.wenxing.wenxingtools.network.packet.AuthorityDataSyncPacket;
import com.wenxing.wenxingtools.network.packet.KeyPacket;
import com.wenxing.wenxingtools.network.packet.ResourceAmpTogglePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "3";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WenXingTools.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(id++, KeyPacket.class, KeyPacket::encode, KeyPacket::decode, KeyPacket::handle);
        INSTANCE.registerMessage(id++, AuthorityDataSyncPacket.class, AuthorityDataSyncPacket::encode, AuthorityDataSyncPacket::decode, AuthorityDataSyncPacket::handle);
        INSTANCE.registerMessage(id++, ResourceAmpTogglePacket.class, ResourceAmpTogglePacket::encode, ResourceAmpTogglePacket::decode, ResourceAmpTogglePacket::handle);
    }

    public static void syncAuthorityData(ServerPlayer player) {
        if (player == null) {
            return;
        }
        IAuthorityData data = AuthorityDataProvider.getData(player);
        if (data != null) {
            INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new AuthorityDataSyncPacket(data.serializeNBT()));
        }
    }
}
