package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.UUID;

public final class AmpConstants {


    public static final float PERCENT_PER_LEVEL = 1.0f;


    public static final int RESOURCE_XP_CAP = 2000000000;


    public static final int EXTRA_LOOT_ROLLS = 2;


    public static final long RESOURCE_XP_MULTIPLIER = 100L;


    public static final double LIFE_LUCK_AMOUNT = 1024.0D;


    public static final UUID LIFE_LUCK_MODIFIER_ID = UUID.fromString("d8a7b0d5-6b2b-4f10-93cd-6c1e6fb9a7d0");


    public static final long POTION_AMPLIFICATION_HOUR_TICKS = 72000L;


    public static final int POTION_AMPLIFICATION_SAFE_DURATION_CAP = 48 * 72_000;


    public static final String RESOURCE_AMPLIFICATION_PLAYER_TAG = WenXingTools.MODID + ":resource_amp_enabled";


    public static final int RESOURCE_AMPLIFICATION_SWITCH_COOLDOWN_MS = 250;


    public static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };


    static final String ATTACK_AMPLIFICATION_LEVEL_TAG = WenXingTools.MODID + ":attack_amp_level";

    private AmpConstants() {
    }
}
