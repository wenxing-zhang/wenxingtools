package com.wenxing.wenxingtools.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ResourceAmplificationPickaxeItem extends PickaxeItem {


    private static final int BUILTIN_RESOURCE_AMPLIFICATION_LEVEL = 2;
    private static final int BUILTIN_ATTACK_AMPLIFICATION_LEVEL = 5;
    private static final int BUILTIN_SPAWN_EGG_HARVEST_LEVEL = 2;
    private static final int BUILTIN_UNBREAKING_LEVEL = 3;
    private static final int BUILTIN_FORTUNE_LEVEL = 3;
    private static final double PICKAXE_ATTACK_DAMAGE = 1000.0D;
    private static final double TARGET_ATTACK_SPEED = 10.0D;
    private static final UUID BASE_ATTACK_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID BASE_ATTACK_SPEED_UUID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    public ResourceAmplificationPickaxeItem() {
        super(Tiers.NETHERITE, 0, 0.0F,
                new Item.Properties()
                        .stacksTo(1)
                        .fireResistant()
                        .rarity(Rarity.EPIC));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> original = super.getDefaultAttributeModifiers(slot);
        if (slot != EquipmentSlot.MAINHAND) {
            return original;
        }

        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        for (Map.Entry<Attribute, AttributeModifier> e : original.entries()) {
            Attribute attr = e.getKey();
            if (attr == Attributes.ATTACK_DAMAGE || attr == Attributes.ATTACK_SPEED) {
                continue;
            }
            builder.put(attr, e.getValue());
        }

        builder.put(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", PICKAXE_ATTACK_DAMAGE, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED,
                new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", TARGET_ATTACK_SPEED, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }


    private static final String BUILTIN_FLAG = "WX_BuiltinEnch";

    public static void applyBuiltinEnchantments(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        Map<Enchantment, Integer> current = new HashMap<>(EnchantmentHelper.getEnchantments(stack));
        current.put(ModEnchantments.RESOURCE_AMPLIFICATION.get(), BUILTIN_RESOURCE_AMPLIFICATION_LEVEL);
        current.put(ModEnchantments.ATTACK_AMPLIFICATION.get(), BUILTIN_ATTACK_AMPLIFICATION_LEVEL);
        current.put(ModEnchantments.SPAWN_EGG_HARVEST.get(), BUILTIN_SPAWN_EGG_HARVEST_LEVEL);

        current.putIfAbsent(Enchantments.UNBREAKING, BUILTIN_UNBREAKING_LEVEL);

        current.putIfAbsent(Enchantments.BLOCK_FORTUNE, BUILTIN_FORTUNE_LEVEL);
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
        int hasResource = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.RESOURCE_AMPLIFICATION.get(), stack);
        int hasAttack = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ATTACK_AMPLIFICATION.get(), stack);
        int hasHarvest = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.SPAWN_EGG_HARVEST.get(), stack);
        if (flagged && hasResource == BUILTIN_RESOURCE_AMPLIFICATION_LEVEL && hasAttack == BUILTIN_ATTACK_AMPLIFICATION_LEVEL
                && hasHarvest == BUILTIN_SPAWN_EGG_HARVEST_LEVEL) {
            return;
        }
        applyBuiltinEnchantments(stack);
    }



    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(0, attacker, (e) -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F) {
            stack.hurtAndBreak(0, miner, (e) -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
        return true;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {


        if (state.is(Blocks.BEDROCK)) {
            return super.getDestroySpeed(stack, state);
        }
        float hardness = state.getDestroySpeed(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (hardness <= 0.0F) {
            return super.getDestroySpeed(stack, state);
        }
        return hardness * 6.0F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {

        return !state.is(Blocks.BEDROCK);
    }

    @Override
    public boolean isCorrectToolForDrops(BlockState state) {

        return !state.is(Blocks.BEDROCK);
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment.category.canEnchant(stack.getItem()) || enchantment == ModEnchantments.RESOURCE_AMPLIFICATION.get();
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return true;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return Tiers.NETHERITE.getEnchantmentValue();
    }
}
