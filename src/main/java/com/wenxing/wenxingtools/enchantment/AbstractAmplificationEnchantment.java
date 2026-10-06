package com.wenxing.wenxingtools.enchantment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public abstract class AbstractAmplificationEnchantment extends Enchantment {


    private static final TagKey<Item> BOWS_TAG =
            ItemTags.create(new ResourceLocation("c", "bows"));
    private static final TagKey<Item> TOOLS_TAG =
            ItemTags.create(new ResourceLocation("c", "tools"));
    private static final TagKey<Item> WEAPONS_TAG =
            ItemTags.create(new ResourceLocation("c", "weapons"));

    protected AbstractAmplificationEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot[] slots) {
        super(rarity, category, slots);
    }


    protected static boolean isRangedBow(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof BowItem || item instanceof CrossbowItem) {
            return true;
        }
        return stack.is(BOWS_TAG);
    }


    protected static boolean isWeaponOrTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof net.minecraft.world.item.SwordItem
                || item instanceof net.minecraft.world.item.DiggerItem) {
            return true;
        }
        return stack.is(TOOLS_TAG) || stack.is(WEAPONS_TAG) || isRangedBow(stack);
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public int getMinCost(int level) {
        return 10;
    }

    @Override
    public int getMaxCost(int level) {
        return 15;
    }


    @Override
    public boolean isTreasureOnly() {
        return true;
    }


    @Override
    public boolean isTradeable() {
        return true;
    }


    @Override
    public boolean isDiscoverable() {
        return true;
    }
}
