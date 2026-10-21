package com.wenxing.wenxingtools.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.wenxing.wenxingtools.util.MixinMarkers;
import com.wenxing.wenxingtools.util.MixinSelfCheck;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
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
 * 不写 {@code require = 0}：那会让注入点打空时静默。现统一走配置的 defaultRequire，打空即本 Mixin
 * 应用失败、启动期抛 InjectionError（游戏直接起不来，启动自检没有机会运行）。所以注入方式必须选不与
 * 他人互斥的：过贵上限用 MixinExtras 的 {@code @ModifyExpressionValue}（wrap 链，可多模组叠加），
 * 不再用独占同一常量节点的 {@code @ModifyConstant}——后者与 Apothic Enchanting 冲突过一次启动崩溃。
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

    /**
     * createResult 中 intValue=40 共 3 处：多件惩罚 / 改名特判 / 过贵清空。只放宽过贵清空（第 3 处）。
     * <p>
     * 用 {@code @ModifyExpressionValue} 而非 {@code @ModifyConstant}：后者对同一常量节点是独占的。
     * Apothic Enchanting 的 AnvilMenuMixin 以 priority 1000 对 createResult 里全部 intValue=40 做
     * ModifyConstant（{@code apoth_removeLevelCap}），本 Mixin 的注入会被跳过，在 defaultRequire=1 下
     * 抛 InjectionError 使启动崩溃（1.2.8 实机事故）。MixinExtras 的 wrap 链允许多个模组同时修改
     * 同一表达式，所以有 Apothic 时两者叠加，没有 Apothic 时本 Mixin 独自生效。
     */
    @ModifyExpressionValue(
            method = "createResult",
            at = @At(value = "CONSTANT", args = "intValue=40", ordinal = 2)
    )
    private int removeTooExpensiveLimit(int original) {
        if (!MixinSelfCheck.isAnvilFallbackEnabled()) {
            return original;
        }
        return Integer.MAX_VALUE;
    }
}
