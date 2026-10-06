package com.wenxing.wenxingtools.client;

import com.wenxing.wenxingtools.attachment.AuthorityAttachments;
import com.wenxing.wenxingtools.attachment.IAuthorityData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientSyncHandler {
    private ClientSyncHandler() {}

    public static void handleAuthorityDataSync(CompoundTag tag) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        IAuthorityData data = AuthorityAttachments.getDataClient(player);
        if (data != null) {
            data.deserializeNBT(tag);
        }
    }
}
