package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.entity.MultiStackItemEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦住原版邻居合并，保护 {@link MultiStackItemEntity}。
 * <p>
 * {@code isMergable}/{@code tryToMerge} 为 private，子类无法覆写；
 * 原版合并只更新显示栈，会与 stacks 失步甚至整体 discard。
 * MultiStack 一律视为不可合并，普通 ItemEntity 之间仍按原版堆叠。
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMergeGuardMixin {

    @Inject(method = "isMergable", at = @At("HEAD"), cancellable = true)
    private void wenxingtools$multiStackNotMergable(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof MultiStackItemEntity) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true)
    private void wenxingtools$skipMultiStackMerge(ItemEntity other, CallbackInfo ci) {
        if ((Object) this instanceof MultiStackItemEntity || other instanceof MultiStackItemEntity) {
            ci.cancel();
        }
    }
}
