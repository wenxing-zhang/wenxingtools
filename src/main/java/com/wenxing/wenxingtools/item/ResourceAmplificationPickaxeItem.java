package com.wenxing.wenxingtools.item;

import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

public class ResourceAmplificationPickaxeItem extends PickaxeItem {

    // 硬编码内置等级与攻击力（对齐 1.20.1 产品数值，不走配置）
    /** 镐面板目标攻击力（玩家基础 1 + 修饰 999 = 1000） */
    private static final double TARGET_ATTACK_DAMAGE = 1000.0D;
    private static final double TARGET_ATTACK_SPEED = 10.0D;
    private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0D;
    private static final double PLAYER_BASE_ATTACK_SPEED = 4.0D;
    private static final double ATTACK_DAMAGE_MODIFIER = TARGET_ATTACK_DAMAGE - PLAYER_BASE_ATTACK_DAMAGE;
    private static final double ATTACK_SPEED_MODIFIER = TARGET_ATTACK_SPEED - PLAYER_BASE_ATTACK_SPEED;
    private static final ResourceLocation ATTACK_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath("minecraft", "base_attack_damage");
    private static final ResourceLocation ATTACK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("minecraft", "base_attack_speed");
    /**
     * 展示/旁路用基础挖掘速度。主手持镐时实际用时由
     * {@link com.wenxing.wenxingtools.mixin.BlockStateDestroyProgressMixin} 固定为约 0.25 秒。
     */
    private static final float UNIVERSAL_DESTROY_SPEED = 20.0F;
    /** 可挖方块目标用时：0.25 秒 = 5 tick */
    public static final int FIXED_MINE_TICKS = 5;
    /** 资源增幅可高于附魔表 max_level=1 */
    private static final int BUILTIN_RESOURCE_AMPLIFICATION_LEVEL = 2;
    private static final int BUILTIN_ATTACK_AMPLIFICATION_LEVEL = 5;
    /** 掠蛋：击杀掉对应刷怪蛋，数量 = 等级（内置硬编码，不走配置） */
    private static final int BUILTIN_SPAWN_EGG_HARVEST_LEVEL = 2;
    /** 回响：命中追加 10×等级 次虚空伤害；可高于附魔表 max_level=3 */
    private static final int BUILTIN_ECHO_AMPLIFICATION_LEVEL = 5;
    /** 净化：命中清药水并开 10×等级 秒持续窗 */
    private static final int BUILTIN_EFFECT_PURGE_LEVEL = 3;
    private static final int BUILTIN_UNBREAKING_LEVEL = 3;
    private static final int BUILTIN_FORTUNE_LEVEL = 3;

    public ResourceAmplificationPickaxeItem() {
        super(Tiers.NETHERITE, new Item.Properties()
                .stacksTo(1)
                .fireResistant()
                .rarity(Rarity.EPIC)
                .attributes(createCustomAttributes())
                // 永久不可破坏：组件层禁止耐久损耗，并显示 Unbreakable
                .component(DataComponents.UNBREAKABLE, new Unbreakable(true)));
    }

    private static ItemAttributeModifiers createCustomAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(ATTACK_DAMAGE_ID, ATTACK_DAMAGE_MODIFIER, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(ATTACK_SPEED_ID, ATTACK_SPEED_MODIFIER, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /** 基岩不可挖掘；其余方块（含模组方块）均可收获 */
    public static boolean canHarvest(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return !state.is(Blocks.BEDROCK);
    }

    public static void applyBuiltinEnchantments(ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        raiseBuiltinEnchantment(stack, ModEnchantments.RESOURCE_AMPLIFICATION, BUILTIN_RESOURCE_AMPLIFICATION_LEVEL, player);
        raiseBuiltinEnchantment(stack, ModEnchantments.ATTACK_AMPLIFICATION, BUILTIN_ATTACK_AMPLIFICATION_LEVEL, player);
        raiseBuiltinEnchantment(stack, ModEnchantments.SPAWN_EGG_HARVEST, BUILTIN_SPAWN_EGG_HARVEST_LEVEL, player);
        raiseBuiltinEnchantment(stack, ModEnchantments.ECHO_AMPLIFICATION, BUILTIN_ECHO_AMPLIFICATION_LEVEL, player);
        raiseBuiltinEnchantment(stack, ModEnchantments.EFFECT_PURGE, BUILTIN_EFFECT_PURGE_LEVEL, player);

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(
                stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        if (player != null) {
            player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                    .get(Enchantments.UNBREAKING)
                    .ifPresent(holder -> mutable.set(holder, Math.max(mutable.getLevel(holder), BUILTIN_UNBREAKING_LEVEL)));
            player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                    .get(Enchantments.FORTUNE)
                    .ifPresent(holder -> mutable.set(holder, Math.max(mutable.getLevel(holder), BUILTIN_FORTUNE_LEVEL)));
        }
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    /** 内置附魔下限写入：仅当现有等级低于下限才写回，避免覆盖铁砧/指令提升的更高等级。 */
    private static void raiseBuiltinEnchantment(ItemStack stack, ResourceKey<Enchantment> key, int minLevel, LivingEntity context) {
        Optional<Holder.Reference<Enchantment>> holderOpt = EnchantUtil.resolveHolder(key, context);
        if (holderOpt.isEmpty()) {
            return;
        }
        Holder<Enchantment> holder = holderOpt.get();
        ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (current.getLevel(holder) >= minLevel) {
            return;
        }
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        mutable.set(holder, minLevel);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    /** 全部内置附魔项是否已达内置下限（纯组件判断，不触碰注册表）。 */
    private static boolean hasAllBuiltinEnchantments(ItemEnchantments enchantments) {
        return hasEnchantmentAtLeast(enchantments, ModEnchantments.RESOURCE_AMPLIFICATION, BUILTIN_RESOURCE_AMPLIFICATION_LEVEL)
                && hasEnchantmentAtLeast(enchantments, ModEnchantments.ATTACK_AMPLIFICATION, BUILTIN_ATTACK_AMPLIFICATION_LEVEL)
                && hasEnchantmentAtLeast(enchantments, ModEnchantments.SPAWN_EGG_HARVEST, BUILTIN_SPAWN_EGG_HARVEST_LEVEL)
                && hasEnchantmentAtLeast(enchantments, ModEnchantments.ECHO_AMPLIFICATION, BUILTIN_ECHO_AMPLIFICATION_LEVEL)
                && hasEnchantmentAtLeast(enchantments, ModEnchantments.EFFECT_PURGE, BUILTIN_EFFECT_PURGE_LEVEL);
    }

    private static boolean hasEnchantmentAtLeast(ItemEnchantments enchantments, ResourceKey<Enchantment> key, int minLevel) {
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.unwrapKey().map(key::equals).orElse(false)) {
                return enchantments.getLevel(holder) >= minLevel;
            }
        }
        return false;
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        applyBuiltinEnchantments(stack, player);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        // 创造栏/指令给予的物品不会走 onCraftedBy，首次入包时补齐内置附魔（仅服务端）
        if (level.isClientSide) {
            return;
        }
        if (!(entity instanceof Player player)) {
            return;
        }
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        // 内置项齐全才跳过：存量镐缺项、或被铁砧等路径改写低于下限时，入包后补回（纯组件判断，不触碰注册表）
        if (stack.has(DataComponents.UNBREAKABLE) && hasAllBuiltinEnchantments(enchantments)) {
            return;
        }
        applyBuiltinEnchantments(stack, player);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // Unbreakable 组件已禁止损耗；此处显式 0 作为双保险
        stack.hurtAndBreak(0, attacker, EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, net.minecraft.core.BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F) {
            stack.hurtAndBreak(0, miner, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (!canHarvest(state)) {
            return super.getDestroySpeed(stack, state);
        }
        // 可挖掘方块统一高速度（实际用时由 Mixin 固定为 5 tick，此值用于旁路/展示）
        return UNIVERSAL_DESTROY_SPEED;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        // 除基岩外，所有方块（含模组方块）均可收获掉落
        return canHarvest(state);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("tooltip.wenxingtools.resource_pickaxe.toggle").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.wenxingtools.resource_pickaxe.mine_all").withStyle(ChatFormatting.DARK_GRAY));
    }
}
