package com.wenxing.wenxingtools.client;

import com.wenxing.wenxingtools.capability.AuthorityDataProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientSyncHandler {
    private ClientSyncHandler() {
    }

    public static void handleAuthorityDataSync(CompoundTag tag) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        player.getCapability(AuthorityDataProvider.AUTHORITY_DATA).ifPresent(data -> data.deserializeNBT(tag));
    }
}
