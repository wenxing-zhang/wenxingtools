package com.wenxing.wenxingtools.attachment;

import net.minecraft.nbt.CompoundTag;

public interface IAuthorityData {
    boolean isDirty();

    void clearDirty();

    boolean isInvincibleCharacteristicOn();

    void setInvincibleCharacteristic(boolean active);

    float getLockedHealth();

    void setLockedHealth(float health);

    boolean isResourceCharacteristicOn();

    void setResourceCharacteristic(boolean active);

    int getResourceMultiplier();

    void setResourceMultiplier(int multiplier);

    boolean isFreedomCharacteristicOn();

    void setFreedomCharacteristic(boolean active);

    boolean isKillAuraCharacteristicOn();

    void setKillAuraCharacteristic(boolean active);

    int getKillAuraMode();

    void setKillAuraMode(int mode);

    void copyFrom(IAuthorityData source);

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag nbt);
}
