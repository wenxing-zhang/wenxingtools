package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.event.EffectPurgeSupport;
import com.wenxing.wenxingtools.util.MixinSelfCheck;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 净化持续窗内：直接拦截 {@code addEffect} / {@code forceAddEffect}，拒绝药水写入。
 * <p>
 * 比事件层更前：覆盖不经 Applicable 的强制写入路径；与每 tick 全量清、Added 结束后全量再清形成多层保险。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPurgeEffectMixin {

    @Inject(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void wenxingtools$blockAddEffect(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if (wenxingtools$shouldBlockEffectWrite()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void wenxingtools$blockAddEffectFrom(
            MobEffectInstance effect,
            Entity source,
            CallbackInfoReturnable<Boolean> cir) {
        if (wenxingtools$shouldBlockEffectWrite()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "forceAddEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void wenxingtools$blockForceAddEffect(
            MobEffectInstance effect,
            Entity source,
            CallbackInfo ci) {
        if (wenxingtools$shouldBlockEffectWrite()) {
            ci.cancel();
        }
    }

    private boolean wenxingtools$shouldBlockEffectWrite() {
        if (!MixinSelfCheck.isPurgeEffectBlockEnabled()) {
            return false;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        return EffectPurgeSupport.isSuppressed(self);
    }
}
