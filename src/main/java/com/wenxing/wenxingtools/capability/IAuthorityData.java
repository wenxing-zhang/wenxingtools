package com.wenxing.wenxingtools.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

public interface IAuthorityData extends INBTSerializable<CompoundTag> {
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

    boolean isDirty();
    void clearDirty();
}
