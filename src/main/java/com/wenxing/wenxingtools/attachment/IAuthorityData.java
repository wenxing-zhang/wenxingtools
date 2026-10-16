package com.wenxing.wenxingtools.attachment;

import net.minecraft.nbt.CompoundTag;

public interface IAuthorityData {
    /** 本次登录是否真的从存档 NBT 反序列化过（false = 存档里没有这份数据）。 */
    boolean wasLoadedFromNbt();

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

    /** 自由飞行是否处于飞行中（瞬时时态，不跨会话 / 死亡继承）。 */
    boolean isFreedomFlying();

    void setFreedomFlying(boolean flying);

    boolean isKillAuraCharacteristicOn();

    void setKillAuraCharacteristic(boolean active);

    int getKillAuraMode();

    void setKillAuraMode(int mode);

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag nbt);
}
