package com.wenxing.wenxingtools.item;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 生命增幅胸甲：铁甲外观，内置 护甲/韧性 1000 与 9 项附魔，不可破坏。
 * <p>
 * 护甲/韧性高于原版属性上限，需 {@link com.wenxing.wenxingtools.util.ArmorAttributeUncap} 抬高上限，
 * 并由 {@link com.wenxing.wenxingtools.mixin.CombatRulesMixin} 放开减伤上界，否则数值会被静默截断。
 */
public class LifeAmplificationChestplateItem extends ArmorItem {

    public static final double BUILTIN_ARMOR_VALUE = 1000.0D;
    public static final double BUILTIN_ARMOR_TOUGHNESS_VALUE = 1000.0D;

    private static final int BUILTIN_PROTECTION_LEVEL = 20;
    /** 生命增幅 / 药水增幅按护甲四槽扫描，装在胸甲槽即可生效 */
    private static final int BUILTIN_LIFE_AMPLIFICATION_LEVEL = 5;
    private static final int BUILTIN_POTION_AMPLIFICATION_LEVEL = 5;
    private static final int BUILTIN_UNBREAKING_LEVEL = 3;
    private static final int BUILTIN_MENDING_LEVEL = 1;
    private static final int BUILTIN_THORNS_LEVEL = 3;
    private static final int BUILTIN_BLAST_PROTECTION_LEVEL = 4;
    private static final int BUILTIN_FIRE_PROTECTION_LEVEL = 4;
    private static final int BUILTIN_PROJECTILE_PROTECTION_LEVEL = 4;

    private static final ResourceLocation ARMOR_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(WenXingTools.MODID, "chestplate_armor");
    private static final ResourceLocation TOUGHNESS_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(WenXingTools.MODID, "chestplate_armor_toughness");

    public LifeAmplificationChestplateItem() {
        super(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE, new Item.Properties()
                .stacksTo(1)
                .fireResistant()
                .rarity(Rarity.EPIC)
                .attributes(createCustomAttributes())
                .component(DataComponents.UNBREAKABLE, new Unbreakable(true)));
    }

    /** 显式属性组件覆盖铁甲材质的 6/0，避免与材质默认值叠加。 */
    private static ItemAttributeModifiers createCustomAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR,
                        new AttributeModifier(ARMOR_MODIFIER_ID, BUILTIN_ARMOR_VALUE, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.CHEST)
                .add(Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(TOUGHNESS_MODIFIER_ID, BUILTIN_ARMOR_TOUGHNESS_VALUE, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.CHEST)
                .build();
    }

    public static void applyBuiltinEnchantments(ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        if (player == null) {
            return;
        }
        EnchantUtil.resolveHolder(ModEnchantments.LIFE_AMPLIFICATION, player)
                .ifPresent(holder -> BuiltinEnchantmentSupport.raiseAtLeast(stack, holder, BUILTIN_LIFE_AMPLIFICATION_LEVEL));
        EnchantUtil.resolveHolder(ModEnchantments.POTION_AMPLIFICATION, player)
                .ifPresent(holder -> BuiltinEnchantmentSupport.raiseAtLeast(stack, holder, BUILTIN_POTION_AMPLIFICATION_LEVEL));

        HolderLookup.RegistryLookup<Enchantment> lookup = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        raiseVanillaEnchantment(stack, lookup, Enchantments.PROTECTION, BUILTIN_PROTECTION_LEVEL);
        raiseVanillaEnchantment(stack, lookup, Enchantments.UNBREAKING, BUILTIN_UNBREAKING_LEVEL);
        raiseVanillaEnchantment(stack, lookup, Enchantments.MENDING, BUILTIN_MENDING_LEVEL);
        raiseVanillaEnchantment(stack, lookup, Enchantments.THORNS, BUILTIN_THORNS_LEVEL);
        raiseVanillaEnchantment(stack, lookup, Enchantments.BLAST_PROTECTION, BUILTIN_BLAST_PROTECTION_LEVEL);
        raiseVanillaEnchantment(stack, lookup, Enchantments.FIRE_PROTECTION, BUILTIN_FIRE_PROTECTION_LEVEL);
        raiseVanillaEnchantment(stack, lookup, Enchantments.PROJECTILE_PROTECTION, BUILTIN_PROJECTILE_PROTECTION_LEVEL);
    }

    private static void raiseVanillaEnchantment(ItemStack stack, HolderLookup.RegistryLookup<Enchantment> lookup,
                                                ResourceKey<Enchantment> key, int minLevel) {
        lookup.get(key).ifPresent(holder -> BuiltinEnchantmentSupport.raiseAtLeast(stack, holder, minLevel));
    }

    /** 全部内置附魔项是否已达内置下限（纯组件判断，不触碰注册表）。 */
    private static boolean hasAllBuiltinEnchantments(ItemEnchantments enchantments) {
        return BuiltinEnchantmentSupport.hasAtLeast(enchantments, ModEnchantments.LIFE_AMPLIFICATION, BUILTIN_LIFE_AMPLIFICATION_LEVEL)
                && BuiltinEnchantmentSupport.hasAtLeast(enchantments, ModEnchantments.POTION_AMPLIFICATION, BUILTIN_POTION_AMPLIFICATION_LEVEL)
                && BuiltinEnchantmentSupport.hasAtLeast(enchantments, Enchantments.PROTECTION, BUILTIN_PROTECTION_LEVEL)
                && BuiltinEnchantmentSupport.hasAtLeast(enchantments, Enchantments.UNBREAKING, BUILTIN_UNBREAKING_LEVEL)
                && BuiltinEnchantmentSupport.hasAtLeast(enchantments, Enchantments.MENDING, BUILTIN_MENDING_LEVEL)
                && BuiltinEnchantmentSupport.hasAtLeast(enchantments, Enchantments.THORNS, BUILTIN_THORNS_LEVEL)
                && BuiltinEnchantmentSupport.hasAtLeast(enchantments, Enchantments.BLAST_PROTECTION, BUILTIN_BLAST_PROTECTION_LEVEL)
                && BuiltinEnchantmentSupport.hasAtLeast(enchantments, Enchantments.FIRE_PROTECTION, BUILTIN_FIRE_PROTECTION_LEVEL)
                && BuiltinEnchantmentSupport.hasAtLeast(enchantments, Enchantments.PROJECTILE_PROTECTION, BUILTIN_PROJECTILE_PROTECTION_LEVEL);
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
        if (stack.has(DataComponents.UNBREAKABLE) && hasAllBuiltinEnchantments(enchantments)) {
            return;
        }
        applyBuiltinEnchantments(stack, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("tooltip.wenxingtools.life_chestplate.note").withStyle(ChatFormatting.GRAY));
    }
}
