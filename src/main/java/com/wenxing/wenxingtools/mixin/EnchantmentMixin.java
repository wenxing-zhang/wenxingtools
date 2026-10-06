package com.wenxing.wenxingtools.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public class EnchantmentMixin {
    @Inject(method = "getFullname", at = @At("HEAD"), cancellable = true, require = 1)
    private void modifyFullname(int level, CallbackInfoReturnable<Component> cir) {
        Enchantment enchantment = (Enchantment) (Object) this;
        MutableComponent name = Component.translatable(enchantment.getDescriptionId());

        if (enchantment.isCurse()) {
            name.withStyle(ChatFormatting.RED);
        } else {
            name.withStyle(ChatFormatting.GRAY);
        }
        if (level != 1 || enchantment.getMaxLevel() != 1) {

            Component levelText = level <= 10
                    ? Component.translatable("enchantment.level." + level)
                    : Component.literal(String.valueOf(level));
            name.append(" ").append(levelText.copy().withStyle(ChatFormatting.GRAY));
        }
        cir.setReturnValue(name);
    }
}
