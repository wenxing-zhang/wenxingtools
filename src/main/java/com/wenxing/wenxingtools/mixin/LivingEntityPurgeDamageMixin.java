package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.event.EffectPurgeEvents;
import com.wenxing.wenxingtools.event.EffectPurgeSupport;
import com.wenxing.wenxingtools.util.CombatUtil;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityPurgeDamageMixin {

    @ModifyVariable(
            method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private DamageSource wenxingtools$purgeToVoid(DamageSource source) {
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
            DamageSource converted = EffectPurgeEvents.resolvePurgeVoidSource(source, self);
            return converted != null ? converted : source;
        } finally {
            EffectPurgeSupport.setVoidConverting(false);
        }
    }
}
