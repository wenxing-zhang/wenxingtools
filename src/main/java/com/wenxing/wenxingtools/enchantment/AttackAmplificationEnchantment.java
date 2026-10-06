package com.wenxing.wenxingtools.enchantment;

import com.wenxing.wenxingtools.integration.TaczIntegration;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class AttackAmplificationEnchantment extends AbstractAmplificationEnchantment {
    private static final EquipmentSlot[] APPLICABLE_SLOTS = new EquipmentSlot[]{EquipmentSlot.MAINHAND};

    public AttackAmplificationEnchantment() {
        super(Rarity.COMMON, EnchantmentCategory.WEAPON, APPLICABLE_SLOTS);
    }


    @Override
    public boolean canEnchant(ItemStack stack) {
        return isWeaponOrTool(stack) || TaczIntegration.isTaczGunItem(stack) || super.canEnchant(stack);
    }


    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return isWeaponOrTool(stack) || TaczIntegration.isTaczGunItem(stack) || super.canApplyAtEnchantingTable(stack);
    }
}
