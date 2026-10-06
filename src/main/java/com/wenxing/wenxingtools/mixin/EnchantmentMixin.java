package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.util.MixinSelfCheck;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 附魔名显示：等级 &gt;10 用数字字面量，否则用 enchantment.level.*；统一灰色。
 * 1.21.1 中 Enchantment#getFullname(Holder,int) 是静态方法，回调必须为 static。
 */
@Mixin(Enchantment.class)
public class EnchantmentMixin {
    @Inject(
            method = "getFullname(Lnet/minecraft/core/Holder;I)Lnet/minecraft/network/chat/Component;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void wenxingtools$modifyFullname(
            Holder<Enchantment> enchantmentHolder,
            int level,
            CallbackInfoReturnable<Component> cir) {
        if (!MixinSelfCheck.isEnchantNameOverrideEnabled()) {
            return;
        }
        Enchantment enchantment;
        try {
            enchantment = enchantmentHolder.value();
        } catch (Throwable t) {
            return;
        }
        MutableComponent name;
        try {
            name = enchantment.description().copy();
        } catch (Throwable t) {
            return;
        }
        name.withStyle(ChatFormatting.GRAY);
        if (level != 1 || enchantment.getMaxLevel() != 1) {
            Component levelText = level <= 10
                    ? Component.translatable("enchantment.level." + level)
                    : Component.literal(String.valueOf(level));
            name.append(" ").append(levelText.copy().withStyle(ChatFormatting.GRAY));
        }
        cir.setReturnValue(name);
    }
}
