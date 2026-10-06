package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.util.MixinSelfCheck;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 护甲减伤上限扩展：原版把护甲贡献锁在 20.0F（80% 减伤），使高于 20 的护甲值没有任何收益。
 * 这里按真实护甲值重算减伤，并只在结果更小时替换 —— 任何情况下都不会让目标更痛。
 */
@Mixin(CombatRules.class)
public abstract class CombatRulesMixin {

    private static final float UNCAPPED_MITIGATION_LIMIT = 0.99F;

    @Inject(method = "getDamageAfterAbsorb", at = @At("RETURN"), cancellable = true, require = 0)
    private static void wenxingtools$uncappedArmorMitigation(
            LivingEntity entity, float damage, DamageSource source, float armorValue, float armorToughness,
            CallbackInfoReturnable<Float> cir) {
        if (!MixinSelfCheck.isUncappedArmorEnabled()) {
            return;
        }
        float ratio = Mth.clamp(armorValue / CombatRules.ARMOR_PROTECTION_DIVIDER, 0.0F, UNCAPPED_MITIGATION_LIMIT);
        float reduced = damage * (1.0F - ratio);
        if (reduced < cir.getReturnValueF()) {
            cir.setReturnValue(reduced);
        }
    }
}
