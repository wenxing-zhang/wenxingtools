package com.wenxing.wenxingtools.attachment;

import net.minecraft.nbt.CompoundTag;

public class AuthorityData implements IAuthorityData {
    private boolean invincibleCharacteristic = false;
    private float lockedHealth = 20.0f;

    private boolean resourceCharacteristic = false;
    private int resourceMultiplier = 10;

    private boolean freedomCharacteristic = false;

    private boolean killAuraCharacteristic = false;
    private int killAuraMode = 0;

    private transient boolean dirty = false;

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
        this.resourceMultiplier = Math.max(1, Math.min(multiplier, 1000));
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
        this.killAuraMode = mode;
        this.dirty = true;
    }

    @Override
    public void copyFrom(IAuthorityData source) {
        this.invincibleCharacteristic = source.isInvincibleCharacteristicOn();
        this.lockedHealth = source.getLockedHealth();
        this.resourceCharacteristic = source.isResourceCharacteristicOn();
        this.resourceMultiplier = source.getResourceMultiplier();
        this.freedomCharacteristic = source.isFreedomCharacteristicOn();
        this.killAuraCharacteristic = source.isKillAuraCharacteristicOn();
        this.killAuraMode = source.getKillAuraMode();
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
            resourceMultiplier = Math.max(1, Math.min(nbt.getInt("resourceMultiplier"), 1000));
        }
        if (nbt.contains("freedomCharacteristic")) {
            freedomCharacteristic = nbt.getBoolean("freedomCharacteristic");
        }
        if (nbt.contains("killAuraCharacteristic")) {
            killAuraCharacteristic = nbt.getBoolean("killAuraCharacteristic");
        }
        if (nbt.contains("killAuraMode")) {
            int loadedMode = nbt.getInt("killAuraMode");
            killAuraMode = loadedMode == 1 ? 1 : 0;
        }
        this.dirty = false;
    }
}
