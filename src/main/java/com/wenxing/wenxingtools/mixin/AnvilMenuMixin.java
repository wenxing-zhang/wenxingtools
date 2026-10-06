package com.wenxing.wenxingtools.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = AnvilMenu.class, priority = 999)
public abstract class AnvilMenuMixin {

    @Redirect(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;supportsEnchantment(Lnet/minecraft/core/Holder;)Z"),
            expect = 0,
            require = 0
    )
    private boolean allowAnyEnchantmentBySupports(ItemStack stack, Holder<Enchantment> enchantment) {
        return true;
    }

    @Redirect(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;isSupportedItem(Lnet/minecraft/world/item/ItemStack;)Z"),
            expect = 0,
            require = 0
    )
    private boolean allowAnyEnchantmentBySupportedItem(Enchantment enchantment, ItemStack stack) {
        return true;
    }

    @Redirect(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z"),
            expect = 0,
            require = 0
    )
    private boolean allowAnyEnchantment(Enchantment enchantment, ItemStack stack) {
        return true;
    }

    @Redirect(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;areCompatible(Lnet/minecraft/core/Holder;Lnet/minecraft/core/Holder;)Z"),
            expect = 0,
            require = 0
    )
    private boolean allowIncompatibleEnchantmentsByAreCompatible(Holder<Enchantment> a, Holder<Enchantment> b) {
        return true;
    }

    @Redirect(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;isCompatibleWith(Lnet/minecraft/world/item/enchantment/Enchantment;)Z"),
            expect = 0,
            require = 0
    )
    private boolean allowIncompatibleEnchantments(Enchantment enchantment, Enchantment other) {
        return true;
    }

    /** createResult 中 intValue=40 共 3 处：多件惩罚 / 改名特判 / 过贵清空。只放宽过贵清空。 */
    @ModifyConstant(
            method = "createResult",
            constant = @Constant(intValue = 40, ordinal = 2),
            expect = 0,
            require = 0
    )
    private int removeTooExpensiveLimit(int original) {
        return Integer.MAX_VALUE;
    }
}
