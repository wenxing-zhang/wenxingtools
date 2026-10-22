package com.wenxing.wenxingtools.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.util.OreRectangleGenerator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WenxingPickaxeItem extends PickaxeItem {


    private static final int BUILTIN_RESOURCE_AMPLIFICATION_LEVEL = 2;
    private static final int BUILTIN_ATTACK_AMPLIFICATION_LEVEL = 5;
    private static final int BUILTIN_SPAWN_EGG_HARVEST_LEVEL = 2;
    private static final int BUILTIN_UNBREAKING_LEVEL = 3;
    private static final int BUILTIN_FORTUNE_LEVEL = 3;
    private static final double PICKAXE_ATTACK_DAMAGE = 1000.0D;
    private static final double TARGET_ATTACK_SPEED = 10.0D;
    private static final UUID BASE_ATTACK_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID BASE_ATTACK_SPEED_UUID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    public WenxingPickaxeItem() {
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
        if (flagged && hasBuiltinEnchantments(stack)) {
            return;
        }
        applyBuiltinEnchantments(stack);
    }

    // 直读附魔列表核对，避免每 tick 构造 HashMap（该物品在背包/装备槽每 tick 触发一次）
    private static boolean hasBuiltinEnchantments(ItemStack stack) {
        ListTag list = stack.getEnchantmentTags();
        int resource = 0;
        int attack = 0;
        int harvest = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(EnchantmentHelper.getEnchantmentId(entry));
            if (enchantment == null) {
                continue;
            }
            if (enchantment == ModEnchantments.RESOURCE_AMPLIFICATION.get()) {
                resource = EnchantmentHelper.getEnchantmentLevel(entry);
            } else if (enchantment == ModEnchantments.ATTACK_AMPLIFICATION.get()) {
                attack = EnchantmentHelper.getEnchantmentLevel(entry);
            } else if (enchantment == ModEnchantments.SPAWN_EGG_HARVEST.get()) {
                harvest = EnchantmentHelper.getEnchantmentLevel(entry);
            }
        }
        return resource == BUILTIN_RESOURCE_AMPLIFICATION_LEVEL
                && attack == BUILTIN_ATTACK_AMPLIFICATION_LEVEL
                && harvest == BUILTIN_SPAWN_EGG_HARVEST_LEVEL;
    }



    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        if (!level.getBlockState(clicked).is(Blocks.OAK_LOG)) {
            return InteractionResult.PASS;
        }
        // 客户端只做预测：返回 SUCCESS 让本地挥手/音效正常，世界改写一律由服务端执行
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        OreRectangleGenerator.generate(serverLevel, clicked, context.getPlayer());
        return InteractionResult.SUCCESS;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        float hardness = state.getDestroySpeed(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (hardness <= 0.0F) {
            return super.getDestroySpeed(stack, state);
        }
        return hardness * 6.0F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        // 与 getDestroyProgress 的强制进度配套：canHarvestBlock 为 false 时 playerDestroy 不被调用，掉落链整段跳过
        return true;
    }

    @Override
    public boolean isCorrectToolForDrops(BlockState state) {
        return true;
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

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.wenxingtools.wenxing_pickaxe.desc")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
