package com.wenxing.wenxingtools.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class PotionAmplificationEnchantment extends AbstractAmplificationEnchantment {

    private static final EquipmentSlot[] APPLICABLE_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    public PotionAmplificationEnchantment() {
        super(Rarity.COMMON, EnchantmentCategory.ARMOR, APPLICABLE_SLOTS);
    }
}
