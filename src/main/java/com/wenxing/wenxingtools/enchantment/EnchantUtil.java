package com.wenxing.wenxingtools.enchantment;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;

import java.util.Optional;

public final class EnchantUtil {
    private EnchantUtil() {}

    public static Optional<Holder.Reference<Enchantment>> resolveHolder(
            ResourceKey<Enchantment> key,
            LivingEntity entity) {
        return entity.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(key);
    }

    public static int getLevel(ResourceKey<Enchantment> key, ItemStack stack, LivingEntity context) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return resolveHolder(key, context)
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
                .orElse(0);
    }

    public static int getLevelOnPlayerSlot(ResourceKey<Enchantment> key, Player player, EquipmentSlot slot) {
        return getLevel(key, player.getItemBySlot(slot), player);
    }

    /** 全装备槽附魔等级合计。 */
    public static int getTotalLevelAllSlots(ResourceKey<Enchantment> key, Player player) {
        int total = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            total += getLevelOnPlayerSlot(key, player, slot);
        }
        return total;
    }

    /**
     * 附魔等级：主手优先，为 0 时回退全槽（覆盖就地改 NBT / 枪械改枪未触发换装）。
     * 对齐 Forge 1.20.1 AmpSupport.resolveEnchantLevel。
     */
    public static int resolveEnchantLevel(ResourceKey<Enchantment> key, Player player) {
        if (player == null) {
            return 0;
        }
        int level = getLevel(key, player.getMainHandItem(), player);
        if (level > 0) {
            return level;
        }
        return getTotalLevelAllSlots(key, player);
    }

    public static int applyEnchantmentOnStack(
            ItemStack stack,
            ResourceKey<Enchantment> key,
            int level,
            LivingEntity context) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Optional<Holder.Reference<Enchantment>> holderOpt = resolveHolder(key, context);
        if (holderOpt.isEmpty()) {
            return 0;
        }
        Holder<Enchantment> holder = holderOpt.get();
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        int applied;
        if (level <= 0) {
            mutable.set(holder, 0);
            applied = 0;
        } else {
            mutable.set(holder, level);
            applied = level;
        }
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return applied;
    }
}
