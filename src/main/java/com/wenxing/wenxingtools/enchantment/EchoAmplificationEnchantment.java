package com.wenxing.wenxingtools.enchantment;

import com.wenxing.wenxingtools.integration.TaczIntegration;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class EchoAmplificationEnchantment extends AbstractAmplificationEnchantment {
    private static final EquipmentSlot[] APPLICABLE_SLOTS = new EquipmentSlot[]{EquipmentSlot.MAINHAND};

    public EchoAmplificationEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.WEAPON, APPLICABLE_SLOTS);
    }

    @Override
    public int getMaxLevel() {
        return 3;
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
