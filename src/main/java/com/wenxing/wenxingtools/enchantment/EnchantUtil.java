package com.wenxing.wenxingtools.enchantment;

import com.wenxing.wenxingtools.util.AmpRules;
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

    /**
     * 装备槽（主手 / 副手 / 护甲）附魔等级取最大。
     * <p>
     * 取最大而非求和：跨槽累加会让双持同名附魔白拿双倍效果，
     * 也与 1.20.1 姊妹端口径一致。对生物而言这些槽位就是它的装备栏，女仆的武器在主手槽即可读到。
     * <p>
     * holder 只解析一次，避免每个槽位各查一次注册表。
     */
    public static int getMaxLevelAllSlots(ResourceKey<Enchantment> key, LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        Optional<Holder.Reference<Enchantment>> holderOpt = resolveHolder(key, entity);
        if (holderOpt.isEmpty()) {
            return 0;
        }
        Holder<Enchantment> holder = holderOpt.get();
        EquipmentSlot[] slots = EquipmentSlot.values();
        int[] levels = new int[slots.length];
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = entity.getItemBySlot(slots[i]);
            if (stack.isEmpty()) {
                continue;
            }
            levels[i] = EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
        }
        return AmpRules.max(levels);
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
