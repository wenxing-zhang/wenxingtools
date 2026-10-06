package com.wenxing.wenxingtools.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AuthorityDataProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    public static final Capability<IAuthorityData> AUTHORITY_DATA = CapabilityManager.get(new CapabilityToken<IAuthorityData>() { });

    public static IAuthorityData getData(ServerPlayer player) {
        if (player == null) return null;
        return player.getCapability(AUTHORITY_DATA).orElse(null);
    }

    private AuthorityData backend = null;

    private LazyOptional<IAuthorityData> optional = LazyOptional.of(this::createAuthorityData);

    private AuthorityData createAuthorityData() {
        if (this.backend == null) {
            this.backend = new AuthorityData();
        }
        return this.backend;
    }

    private LazyOptional<IAuthorityData> liveOptional() {
        if (!this.optional.isPresent()) {
            this.optional = LazyOptional.of(this::createAuthorityData);
        }
        return this.optional;
    }


    public void invalidate() {
        optional.invalidate();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == AUTHORITY_DATA) {
            return liveOptional().cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        if (this.backend == null) {
            this.createAuthorityData();
        }
        return this.backend.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (this.backend == null) {
            this.createAuthorityData();
        }
        this.backend.deserializeNBT(nbt);
    }
}
