package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.util.MixinSelfCheck;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 铁砧：任意附魔可合并、忽略附魔互斥（产品有意设计，不做限制）。
 * <p>
 * 1.21.1 的 {@code AnvilMenu#createResult} 只经两个入口判定能否附上：实例调用
 * {@code ItemStack.supportsEnchantment}（NeoForge 接口扩展方法，其默认实现内部再走
 * {@code Enchantment#isSupportedItem} 与 {@code #canEnchant}），以及静态调用
 * {@code Enchantment.areCompatible}。旧的三个入口（isSupportedItem / canEnchant /
 * isCompatibleWith）在该方法字节码里出现 0 次，其中 isCompatibleWith 在 1.21.1 已不存在——
 * 对它们做重定向是永不命中的死注入点，已删除；放行由上面两个入口完成。
 * <p>
 * 每个回调都先查自检开关，关掉时显式回退到被重定向的原调用：铁砧的两个 Mixin 各自独立落地，
 * 任一方打空都会关掉同一个开关，不查开关就会出现「开关已关、重定向仍在放行」的口径不一致。
 * <p>
 * 不写 {@code require = 0}：那会让注入点打空时静默。现统一走配置的 defaultRequire，
 * 打空即本 Mixin 应用失败，由启动自检的落地标记发现并关闭特性。
 */
@Mixin(value = AnvilMenu.class, priority = 999)
public abstract class AnvilMenuMixin implements MixinMarkers.AnvilAnyEnchantment {

    @Redirect(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;supportsEnchantment(Lnet/minecraft/core/Holder;)Z")
    )
    private boolean allowAnyEnchantmentBySupports(ItemStack stack, Holder<Enchantment> enchantment) {
        if (!MixinSelfCheck.isAnvilFallbackEnabled()) {
            return stack.supportsEnchantment(enchantment);
        }
        return true;
    }

    @Redirect(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;areCompatible(Lnet/minecraft/core/Holder;Lnet/minecraft/core/Holder;)Z")
    )
    private static boolean allowIncompatibleEnchantmentsByAreCompatible(Holder<Enchantment> a, Holder<Enchantment> b) {
        if (!MixinSelfCheck.isAnvilFallbackEnabled()) {
            return Enchantment.areCompatible(a, b);
        }
        return true;
    }

    /** createResult 中 intValue=40 共 3 处：多件惩罚 / 改名特判 / 过贵清空。只放宽过贵清空。 */
    @ModifyConstant(
            method = "createResult",
            constant = @Constant(intValue = 40, ordinal = 2)
    )
    private int removeTooExpensiveLimit(int original) {
        if (!MixinSelfCheck.isAnvilFallbackEnabled()) {
            return original;
        }
        return Integer.MAX_VALUE;
    }
}
