package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.event.EffectPurgeHandler;
import com.wenxing.wenxingtools.event.EffectPurgeSupport;
import com.wenxing.wenxingtools.util.CombatUtil;
import com.wenxing.wenxingtools.util.MixinSelfCheck;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 净化持续窗内：目标 {@code actuallyHurt} 接受的伤害改写为原版虚空 {@code out_of_world}。
 * <p>
 * 选在 actuallyHurt 而非 hurt：已经通过无敌帧/免疫判定，只替换伤害类型；
 * 虚空自带穿透护甲/附魔/盾牌，后续结算按虚空语义走，且只触发一次事件链。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPurgeDamageMixin {

    @ModifyVariable(
            method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private DamageSource wenxingtools$purgeToVoid(DamageSource source) {
        if (!MixinSelfCheck.isPurgeVoidRewriteEnabled()) {
            return source;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide) {
            return source;
        }
        if (EffectPurgeSupport.isVoidConverting()) {
            return source;
        }
        if (!EffectPurgeSupport.isSuppressed(self)) {
            return source;
        }
        if (CombatUtil.isVanillaOutOfWorld(source)) {
            return source;
        }
        EffectPurgeSupport.setVoidConverting(true);
        try {
            DamageSource converted = EffectPurgeHandler.resolvePurgeVoidSource(source, self);
            return converted != null ? converted : source;
        } finally {
            EffectPurgeSupport.setVoidConverting(false);
        }
    }
}
