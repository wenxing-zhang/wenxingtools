package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.entity.FreedomFlightPolicy;
import com.wenxing.wenxingtools.entity.FreedomFlightService;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自由飞行的垂直输入：复刻原版创造飞行在 {@code LocalPlayer.aiStep} 内的那段
 * （跳跃上升 / 潜行下降，每次把 {@code j * flyingSpeed * 3.0} 累加到垂直速度）。
 * <p>
 * 原版那段自身以 {@code abilities.flying} 为条件，但该方法内共有 4 处读该字段
 * （创造模式双击切换、落地自动关飞行等），逐处改写会误伤原版逻辑；因此这里不改写读取，
 * 而是独立注入到 {@code super.aiStep()} 调用点之前，自己判自有状态。
 */
@Mixin(value = LocalPlayer.class, priority = 1500)
public abstract class LocalPlayerFreedomFlightMixin {

    @Inject(
            method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;aiStep()V"),
            require = 1)
    private void wenxingtools$freedomFlightVerticalInput(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (self.isPassenger() || !FreedomFlightService.isFlyingOn(self)) {
            return;
        }
        int vertical = FreedomFlightPolicy.verticalInput(self.input.jumping, self.input.shiftKeyDown);
        if (vertical == 0) {
            return;
        }
        self.setDeltaMovement(self.getDeltaMovement().add(0.0D,
                FreedomFlightPolicy.verticalAccel(vertical, self.getAbilities().getFlyingSpeed()), 0.0D));
    }
}
