package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.UUID;

public final class AmpConstants {


    public static final float PERCENT_PER_LEVEL = 1.0f;


    public static final int RESOURCE_XP_CAP = 2000000000;


    public static final long RESOURCE_XP_MULTIPLIER = 100L;


    /** 资源增幅倍率系数：可堆叠物品每级 ×100 */
    public static final int RESOURCE_FACTOR_STACKABLE_PER_LEVEL = 100;


    /**
     * 资源增幅倍率系数：不可堆叠物品每级 ×10。
     * <p>不可堆叠物品若吃满 ×100，会在单个 MultiStack 实体里塞进上百个单件 ItemStack，
     * NBT 与同步包体量失控，故单独降档。
     */
    public static final int RESOURCE_FACTOR_UNSTACKABLE_PER_LEVEL = 10;


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
