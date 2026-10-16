package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.entity.FlightSeatEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 潜行键在飞行座上只作下降输入，不触发原版通用下马。
 * <p>
 * 1.20.1 的 {@code Player.rideTick()} 会以 {@code wantsToStopRiding()}（默认取潜行键状态）直接
 * {@code stopRiding()}——骑飞行座时按潜行键会被当场弹下，下降输入永远送不到服务端。
 * 只在「当前载具是飞行座」时把该判定置否。
 */
@Mixin(Player.class)
public abstract class PlayerFlightSeatRideMixin {

    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true, require = 1)
    private void wenxingtools$keepRidingFlightSeat(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (self.getVehicle() instanceof FlightSeatEntity) {
            cir.setReturnValue(false);
        }
    }
}
