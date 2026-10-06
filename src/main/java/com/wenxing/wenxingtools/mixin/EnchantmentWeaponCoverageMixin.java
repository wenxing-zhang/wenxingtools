package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.enchantment.ExpandedEnchantCoverage;
import com.wenxing.wenxingtools.util.MixinSelfCheck;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 掠蛋武器覆盖：在 vanilla 标签判定之后追加动态武器放行（TACZ 枪 / 模组武器类）。
 * <p>
 * NeoForge 兼容入口 {@code ItemStack#supportsEnchantment} 默认实现会走
 * {@code Enchantment#isSupportedItem}；第三方（AttributeArch 等）仍可能调
 * {@code canEnchant}。两处都放行，避免「铁砧能附、祭坛不能附」。
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentWeaponCoverageMixin {

    @Inject(method = "isSupportedItem", at = @At("RETURN"), cancellable = true, require = 0)
    private void wenxingtools$expandSupportedItem(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!MixinSelfCheck.isWeaponCoverageExpandEnabled()) {
            return;
        }
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        if (ExpandedEnchantCoverage.allowsSpawnEggHarvest((Enchantment) (Object) this, stack)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "canEnchant", at = @At("RETURN"), cancellable = true, require = 0)
    private void wenxingtools$expandCanEnchant(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!MixinSelfCheck.isWeaponCoverageExpandEnabled()) {
            return;
        }
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        if (ExpandedEnchantCoverage.allowsSpawnEggHarvest((Enchantment) (Object) this, stack)) {
            cir.setReturnValue(true);
        }
    }
}
