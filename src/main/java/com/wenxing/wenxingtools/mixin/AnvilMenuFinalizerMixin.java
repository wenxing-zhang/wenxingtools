package com.wenxing.wenxingtools.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = AnvilMenu.class, priority = 1500)
public abstract class AnvilMenuFinalizerMixin {
    @Shadow @Final private DataSlot cost;

    private Container wenxingtools$getInputSlots() {
        return ((ItemCombinerMenuAccessor) (Object) this).wenxingtools$getInputSlots();
    }

    private ResultContainer wenxingtools$getResultSlots() {
        return ((ItemCombinerMenuAccessor) (Object) this).wenxingtools$getResultSlots();
    }

    @Inject(method = "createResult", at = @At("RETURN"), require = 1)
    private void finalizeAnvilResultAfterAllMods(CallbackInfo ci) {
        applyFallbackForAnyEnchantment();
        applyFixedExperienceCostForcefully();
    }

    private void applyFallbackForAnyEnchantment() {
        Container inputSlots = wenxingtools$getInputSlots();
        ResultContainer resultSlots = wenxingtools$getResultSlots();
        if (inputSlots == null || resultSlots == null) {
            return;
        }

        ItemStack leftStack = inputSlots.getItem(0);
        ItemStack rightStack = inputSlots.getItem(1);

        if (leftStack.isEmpty() || rightStack.isEmpty() || leftStack.getCount() != 1) {
            return;
        }


        if (leftStack.is(Items.ENCHANTED_BOOK)) {
            return;
        }

        Map<Enchantment, Integer> rightEnchants = readRightEnchants(leftStack, rightStack);
        if (rightEnchants.isEmpty()) {
            return;
        }


        ItemStack output = resultSlots.getItem(0);
        if (output.isEmpty() || output.getItem() != leftStack.getItem()) {
            output = leftStack.copy();
        } else {
            output = output.copy();
        }

        Map<Enchantment, Integer> outputEnchants = EnchantmentHelper.getEnchantments(output);
        for (Map.Entry<Enchantment, Integer> bookEntry : rightEnchants.entrySet()) {
            Enchantment ench = bookEntry.getKey();
            int bookLevel = bookEntry.getValue();
            if (ench == null || bookLevel <= 0) {
                continue;
            }
            int currentLevel = outputEnchants.getOrDefault(ench, 0);
            int newLevel;
            if (currentLevel == bookLevel) {
                newLevel = currentLevel + 1;
            } else {
                newLevel = Math.max(currentLevel, bookLevel);
            }
            newLevel = Math.min(newLevel, ench.getMaxLevel());
            outputEnchants.put(ench, newLevel);
        }

        boolean hasAnyValid = false;
        for (Map.Entry<Enchantment, Integer> e : outputEnchants.entrySet()) {
            if (e.getValue() > 0) {
                hasAnyValid = true;
                break;
            }
        }
        if (!hasAnyValid) {
            return;
        }

        EnchantmentHelper.setEnchantments(outputEnchants, output);
        resultSlots.setItem(0, output);
    }


    private static Map<Enchantment, Integer> readRightEnchants(ItemStack leftStack, ItemStack rightStack) {
        if (rightStack.is(Items.ENCHANTED_BOOK)) {
            return readEnchantmentsFromEnchantedBook(rightStack);
        }
        if (rightStack.getItem() == leftStack.getItem() && leftStack.isDamageableItem()) {
            return EnchantmentHelper.getEnchantments(rightStack);
        }
        return new HashMap<>(2);
    }

    private void applyFixedExperienceCostForcefully() {
        ResultContainer resultSlots = wenxingtools$getResultSlots();
        if (resultSlots != null && !resultSlots.getItem(0).isEmpty()) {
            this.cost.set(1);
        }
    }

    private static Map<Enchantment, Integer> readEnchantmentsFromEnchantedBook(ItemStack bookStack) {
        if (bookStack == null || bookStack.isEmpty() || bookStack.getItem() != Items.ENCHANTED_BOOK) {
            return new HashMap<>(2);
        }
        ListTag stored = bookStack.getTag() != null
                ? bookStack.getTag().getList("StoredEnchantments", 10)
                : new ListTag();
        int storedCount = Math.max(2, stored.size());
        Map<Enchantment, Integer> result = new HashMap<>((int) (storedCount / 0.75f) + 1);
        for (int i = 0; i < stored.size(); i++) {
            CompoundTag tag = stored.getCompound(i);
            ResourceLocation id = EnchantmentHelper.getEnchantmentId(tag);
            if (id == null) continue;
            Enchantment ench = BuiltInRegistries.ENCHANTMENT.get(id);
            if (ench == null) continue;
            int level = EnchantmentHelper.getEnchantmentLevel(tag);
            if (level <= 0) continue;
            result.merge(ench, level, Math::max);
        }
        return result;
    }
}
