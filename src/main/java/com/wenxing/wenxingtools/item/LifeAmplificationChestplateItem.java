package com.wenxing.wenxingtools.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LifeAmplificationChestplateItem extends ArmorItem {

    public static final double BUILTIN_ARMOR_VALUE = 1000.0D;
    public static final double BUILTIN_ARMOR_TOUGHNESS_VALUE = 1000.0D;

    public static final int BUILTIN_PROTECTION_LEVEL = 20;
    public static final int BUILTIN_LIFE_AMPLIFICATION_LEVEL = 5;
    public static final int BUILTIN_POTION_AMPLIFICATION_LEVEL = 5;

    private static final int BUILTIN_UNBREAKING_LEVEL = 3;
    private static final int BUILTIN_MENDING_LEVEL = 1;
    private static final int BUILTIN_THORNS_LEVEL = 3;
    private static final int BUILTIN_BLAST_PROTECTION_LEVEL = 4;
    private static final int BUILTIN_FIRE_PROTECTION_LEVEL = 4;
    private static final int BUILTIN_PROJECTILE_PROTECTION_LEVEL = 4;

    private static final UUID CHESTPLATE_ARMOR_UUID = UUID.fromString("9F3D476D-C118-4544-8365-64846904B48E");
    private static final String BUILTIN_FLAG = "WX_BuiltinEnch";

    public LifeAmplificationChestplateItem() {
        super(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE,
                new Item.Properties()
                        .stacksTo(1)
                        .fireResistant()
                        .rarity(Rarity.EPIC));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> original = super.getDefaultAttributeModifiers(slot);
        if (slot != EquipmentSlot.CHEST) {
            return original;
        }

        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        for (Map.Entry<Attribute, AttributeModifier> entry : original.entries()) {
            Attribute attribute = entry.getKey();
            if (attribute == Attributes.ARMOR || attribute == Attributes.ARMOR_TOUGHNESS) {
                continue;
            }
            builder.put(attribute, entry.getValue());
        }

        builder.put(Attributes.ARMOR,
                new AttributeModifier(CHESTPLATE_ARMOR_UUID, "Armor modifier", BUILTIN_ARMOR_VALUE, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ARMOR_TOUGHNESS,
                new AttributeModifier(CHESTPLATE_ARMOR_UUID, "Armor toughness", BUILTIN_ARMOR_TOUGHNESS_VALUE, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    public static void applyBuiltinEnchantments(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Map<Enchantment, Integer> current = new HashMap<>(EnchantmentHelper.getEnchantments(stack));
        // 胸甲禁止诅咒附魔：战利品/交易/铁砧/指令写进来的诅咒一并剔除
        current.entrySet().removeIf(entry -> entry.getKey().isCurse());
        current.put(Enchantments.ALL_DAMAGE_PROTECTION, BUILTIN_PROTECTION_LEVEL);
        current.put(ModEnchantments.LIFE_AMPLIFICATION.get(), BUILTIN_LIFE_AMPLIFICATION_LEVEL);
        current.put(ModEnchantments.POTION_AMPLIFICATION.get(), BUILTIN_POTION_AMPLIFICATION_LEVEL);
        current.putIfAbsent(Enchantments.UNBREAKING, BUILTIN_UNBREAKING_LEVEL);
        current.putIfAbsent(Enchantments.MENDING, BUILTIN_MENDING_LEVEL);
        current.putIfAbsent(Enchantments.THORNS, BUILTIN_THORNS_LEVEL);
        current.putIfAbsent(Enchantments.BLAST_PROTECTION, BUILTIN_BLAST_PROTECTION_LEVEL);
        current.putIfAbsent(Enchantments.FIRE_PROTECTION, BUILTIN_FIRE_PROTECTION_LEVEL);
        current.putIfAbsent(Enchantments.PROJECTILE_PROTECTION, BUILTIN_PROJECTILE_PROTECTION_LEVEL);
        EnchantmentHelper.setEnchantments(current, stack);

        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("Unbreakable", true);
        tag.putBoolean(BUILTIN_FLAG, true);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        applyBuiltinEnchantments(stack);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        applyBuiltinEnchantments(stack);
        return stack;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (level.isClientSide) {
            return;
        }
        CompoundTag tag = stack.getTag();
        boolean flagged = tag != null && tag.getBoolean(BUILTIN_FLAG);
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        if (flagged
                && !hasCurse(enchants)
                && enchants.getOrDefault(Enchantments.ALL_DAMAGE_PROTECTION, 0) == BUILTIN_PROTECTION_LEVEL
                && enchants.getOrDefault(ModEnchantments.LIFE_AMPLIFICATION.get(), 0) == BUILTIN_LIFE_AMPLIFICATION_LEVEL
                && enchants.getOrDefault(ModEnchantments.POTION_AMPLIFICATION.get(), 0) == BUILTIN_POTION_AMPLIFICATION_LEVEL) {
            return;
        }
        applyBuiltinEnchantments(stack);
    }

    private static boolean hasCurse(Map<Enchantment, Integer> enchants) {
        for (Enchantment enchantment : enchants.keySet()) {
            if (enchantment.isCurse()) {
                return true;
            }
        }
        return false;
    }
}
