package com.wenxing.wenxingtools.enchantment;

import com.wenxing.wenxingtools.integration.TaczIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class ResourceAmplificationEnchantment extends AbstractAmplificationEnchantment {
    private static final EquipmentSlot[] APPLICABLE_SLOTS = new EquipmentSlot[]{EquipmentSlot.MAINHAND};
    private static final TagKey<Item> COMMON_TOOLS_TAG =
            ItemTags.create(new ResourceLocation("c", "tools"));

    public ResourceAmplificationEnchantment() {
        super(Rarity.COMMON, EnchantmentCategory.DIGGER, APPLICABLE_SLOTS);
    }


    @Override
    public int getMaxLevel() {
        return 1;
    }


    @Override
    public boolean canEnchant(ItemStack stack) {
        return isWeaponOrTool(stack)
                || stack.is(COMMON_TOOLS_TAG)
                || TaczIntegration.isTaczGunItem(stack)
                || super.canEnchant(stack);
    }


    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return isWeaponOrTool(stack)
                || stack.is(COMMON_TOOLS_TAG)
                || TaczIntegration.isTaczGunItem(stack)
                || super.canApplyAtEnchantingTable(stack);
    }
}
