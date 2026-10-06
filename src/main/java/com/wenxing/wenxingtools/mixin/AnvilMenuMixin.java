package com.wenxing.wenxingtools.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = AnvilMenu.class, priority = 999)
public abstract class AnvilMenuMixin {

    @WrapOperation(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z"),
            require = 0
    )
    private boolean allowAnyEnchantment(Enchantment enchantment, ItemStack stack, Operation<Boolean> original) {
        return true;
    }

    @WrapOperation(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;isCompatibleWith(Lnet/minecraft/world/item/enchantment/Enchantment;)Z"),
            require = 0
    )
    private boolean allowIncompatibleEnchantments(Enchantment enchantment, Enchantment other, Operation<Boolean> original) {
        return true;
    }

    @ModifyConstant(method = "createResult", constant = @Constant(intValue = 40), expect = 0, require = 0)
    private int removeTooExpensiveLimit(int original) {
        return Integer.MAX_VALUE;
    }
}
