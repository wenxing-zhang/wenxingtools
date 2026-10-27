package com.wenxing.wenxingtools.attachment;

import net.minecraft.nbt.CompoundTag;

public class AuthorityData implements IAuthorityData {
    /** 资源特性倍率上限：命令参数、setter 与 NBT 反序列化共用同一常量，避免三处各自写死而漂移。 */
    public static final int MAX_RESOURCE_MULTIPLIER = 1_000_000;

    private boolean invincibleCharacteristic = false;
    private float lockedHealth = 20.0f;

    private boolean resourceCharacteristic = false;
    private int resourceMultiplier = 10;

    private boolean freedomCharacteristic = false;
    private boolean freedomFlying = false;

    private boolean killAuraCharacteristic = false;
    private int killAuraMode = 0;

    private transient boolean dirty = false;
    /** 本次登录是否真的从存档 NBT 反序列化过（用于区分「存档里没有数据」与「数据恰为默认值」）。 */
    private transient boolean loadedFromNbt = false;

    @Override
    public boolean wasLoadedFromNbt() {
        return loadedFromNbt;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void clearDirty() {
        this.dirty = false;
    }

    @Override
    public boolean isInvincibleCharacteristicOn() {
        return invincibleCharacteristic;
    }

    @Override
    public void setInvincibleCharacteristic(boolean active) {
        this.invincibleCharacteristic = active;
        this.dirty = true;
    }

    @Override
    public float getLockedHealth() {
        return lockedHealth;
    }

    @Override
    public void setLockedHealth(float health) {
        this.lockedHealth = health;
        this.dirty = true;
    }

    @Override
    public boolean isResourceCharacteristicOn() {
        return resourceCharacteristic;
    }

    @Override
    public void setResourceCharacteristic(boolean active) {
        this.resourceCharacteristic = active;
        this.dirty = true;
    }

    @Override
    public int getResourceMultiplier() {
        return resourceMultiplier;
    }

    @Override
    public void setResourceMultiplier(int multiplier) {
        this.resourceMultiplier = Math.max(1, Math.min(multiplier, MAX_RESOURCE_MULTIPLIER));
        this.dirty = true;
    }

    @Override
    public boolean isFreedomCharacteristicOn() {
        return freedomCharacteristic;
    }

    @Override
    public void setFreedomCharacteristic(boolean active) {
        this.freedomCharacteristic = active;
        this.dirty = true;
    }

    @Override
    public boolean isFreedomFlying() {
        return freedomFlying;
    }

    @Override
    public void setFreedomFlying(boolean flying) {
        this.freedomFlying = flying;
        this.dirty = true;
    }

    @Override
    public boolean isKillAuraCharacteristicOn() {
        return killAuraCharacteristic;
    }

    @Override
    public void setKillAuraCharacteristic(boolean active) {
        this.killAuraCharacteristic = active;
        this.dirty = true;
    }

    @Override
    public int getKillAuraMode() {
        return killAuraMode;
    }

    @Override
    public void setKillAuraMode(int mode) {
        this.killAuraMode = mode == 1 ? 1 : 0;
        this.dirty = true;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("invincibleCharacteristic", invincibleCharacteristic);
        tag.putFloat("lockedHealth", lockedHealth);
        tag.putBoolean("resourceCharacteristic", resourceCharacteristic);
        tag.putInt("resourceMultiplier", resourceMultiplier);
        tag.putBoolean("freedomCharacteristic", freedomCharacteristic);
        tag.putBoolean("freedomFlying", freedomFlying);
        tag.putBoolean("killAuraCharacteristic", killAuraCharacteristic);
        tag.putInt("killAuraMode", killAuraMode);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("invincibleCharacteristic")) {
            invincibleCharacteristic = nbt.getBoolean("invincibleCharacteristic");
        } else if (nbt.contains("lifeCharacteristic") || nbt.contains("attackCharacteristic")) {
            boolean life = nbt.contains("lifeCharacteristic") && nbt.getBoolean("lifeCharacteristic");
            boolean atk = nbt.contains("attackCharacteristic") && nbt.getBoolean("attackCharacteristic");
            invincibleCharacteristic = life || atk;
        }
        if (nbt.contains("lockedHealth")) {
            float loadedHealth = nbt.getFloat("lockedHealth");
            if (!Float.isNaN(loadedHealth) && !Float.isInfinite(loadedHealth) && loadedHealth >= 0.0f) {
                lockedHealth = loadedHealth;
            }
        }
        if (nbt.contains("resourceCharacteristic")) {
            resourceCharacteristic = nbt.getBoolean("resourceCharacteristic");
        }
        if (nbt.contains("resourceMultiplier")) {
            resourceMultiplier = Math.max(1, Math.min(nbt.getInt("resourceMultiplier"), MAX_RESOURCE_MULTIPLIER));
        }
        if (nbt.contains("freedomCharacteristic")) {
            freedomCharacteristic = nbt.getBoolean("freedomCharacteristic");
        }
        if (nbt.contains("freedomFlying")) {
            freedomFlying = nbt.getBoolean("freedomFlying");
        }
        if (nbt.contains("killAuraCharacteristic")) {
            killAuraCharacteristic = nbt.getBoolean("killAuraCharacteristic");
        }
        if (nbt.contains("killAuraMode")) {
            int loadedMode = nbt.getInt("killAuraMode");
            killAuraMode = loadedMode == 1 ? 1 : 0;
        }
        this.dirty = false;
        this.loadedFromNbt = true;
    }
}
