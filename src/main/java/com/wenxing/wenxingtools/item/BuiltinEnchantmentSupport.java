package com.wenxing.wenxingtools.item;

import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 专属物品「内置附魔下限写入」共享逻辑：仅当现有等级低于下限才写回，
 * 避免覆盖铁砧/指令提升的更高等级；附带的「是否已达下限」为纯组件判断，不触碰注册表。
 */
public final class BuiltinEnchantmentSupport {
    private BuiltinEnchantmentSupport() {}

    /** 按已解析 Holder 抬到下限。 */
    public static void raiseAtLeast(ItemStack stack, Holder<Enchantment> holder, int minLevel) {
        ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (current.getLevel(holder) >= minLevel) {
            return;
        }
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        mutable.set(holder, minLevel);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    /** 按附魔 key 解析 Holder 后抬到下限；解析失败静默跳过。 */
    public static void raiseAtLeast(ItemStack stack, ResourceKey<Enchantment> key, int minLevel, LivingEntity context) {
        EnchantUtil.resolveHolder(key, context).ifPresent(holder -> raiseAtLeast(stack, holder, minLevel));
    }

    /** 纯组件判定：该附魔是否已达下限。 */
    public static boolean hasAtLeast(ItemEnchantments enchantments, ResourceKey<Enchantment> key, int minLevel) {
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.unwrapKey().map(key::equals).orElse(false)) {
                return enchantments.getLevel(holder) >= minLevel;
            }
        }
        return false;
    }
}
