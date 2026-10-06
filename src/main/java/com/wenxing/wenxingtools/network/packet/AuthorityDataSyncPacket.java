package com.wenxing.wenxingtools.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AuthorityDataSyncPacket {
    private final CompoundTag dataTag;

    public AuthorityDataSyncPacket(CompoundTag dataTag) {
        this.dataTag = dataTag;
    }

    public static void encode(AuthorityDataSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.dataTag);
    }

    public static AuthorityDataSyncPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new AuthorityDataSyncPacket(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(AuthorityDataSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            ctx.get().setPacketHandled(true);
            return;
        }
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHooks.handleAuthorityDataSync(msg.dataTag)));
        ctx.get().setPacketHandled(true);
    }


    private static final class ClientPacketHooks {
        private ClientPacketHooks() {}

        private static void handleAuthorityDataSync(CompoundTag tag) {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                return;
            }
            com.wenxing.wenxingtools.client.ClientSyncHandler.handleAuthorityDataSync(tag);
        }
    }
}
