package com.wenxing.wenxingtools.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class LifeAmplificationEnchantment extends AbstractAmplificationEnchantment {
    private static final EquipmentSlot[] APPLICABLE_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    public LifeAmplificationEnchantment() {
        super(Rarity.COMMON, EnchantmentCategory.ARMOR, APPLICABLE_SLOTS);
    }


    @Override
    public boolean canEnchant(ItemStack stack) {
        return super.canEnchant(stack);
    }
}
