package com.wenxing.wenxingtools.attachment;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.jetbrains.annotations.Nullable;

public final class AuthorityDataSerializer implements IAttachmentSerializer<CompoundTag, AuthorityData> {
    @Override
    public AuthorityData read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
        AuthorityData data = new AuthorityData();
        data.deserializeNBT(tag);
        return data;
    }

    @Override
    public @Nullable CompoundTag write(AuthorityData attachment, HolderLookup.Provider provider) {
        return attachment.serializeNBT();
    }
}
