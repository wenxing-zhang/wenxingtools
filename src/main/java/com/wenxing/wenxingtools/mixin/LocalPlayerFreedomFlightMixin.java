package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.entity.FreedomFlightPolicy;
import com.wenxing.wenxingtools.entity.FreedomFlightService;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自由飞行的垂直输入：复刻原版创造飞行在 {@code LocalPlayer.aiStep} 内的那段
 * （跳跃上升 / 潜行下降，每次把 {@code j * flyingSpeed * 3.0} 累加到垂直速度）。
 * <p>
 * 原版那段自身以 {@code abilities.flying} 为条件，但该方法内共有 9 处读该字段
 * （创造模式双击切换、鞘翅、落地自动关飞行等），用 ordinal 逐处改写既脆弱又会误伤原版逻辑；
 * 因此这里不改写读取，而是独立注入到 {@code super.aiStep()} 调用点之前，自己判自有状态。
 * <p>
 * 另接管本方法里「慢速系数」的应用点：原版飞行时 {@code crouching} 恒为 false，
 * 于是 {@code input.tick(isMovingSlowly(), f)} 不会把水平输入乘成 30%。自由飞行不写该能力
 * 字段，{@code crouching} 仍会为真，必须在此显式抑制，否则按住潜行键下降时水平移动会变慢。
 */
@Mixin(value = LocalPlayer.class, priority = 1500)
public abstract class LocalPlayerFreedomFlightMixin {

    /** 上一刻的垂直输入：仅在改变时打诊断日志（正常游戏 debug 关闭，零输出）。 */
    @Unique
    private int wenxingtools$lastVertical = Integer.MIN_VALUE;

    @Inject(
            method = "aiStep",
            // super.aiStep() 的字节码 owner 是直接父类 AbstractClientPlayer（而非 Player），
            // 写成 Player 会匹配不到注入点并抛 InjectionError，勿改回。
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;aiStep()V"),
            require = 1)
    private void wenxingtools$freedomFlightVerticalInput(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (self.isPassenger() || !FreedomFlightService.isFlyingOn(self)) {
            return;
        }
        int vertical = FreedomFlightPolicy.verticalInput(self.input.jumping, self.input.shiftKeyDown);
        double currentY = self.getDeltaMovement().y;
        if (WenXingTools.LOGGER.isDebugEnabled() && vertical != this.wenxingtools$lastVertical) {
            WenXingTools.LOGGER.debug("[freedom_flight] vertical={} jumping={} shift={} dy={}",
                    vertical, self.input.jumping, self.input.shiftKeyDown, (float) currentY);
        }
        this.wenxingtools$lastVertical = vertical;
        if (vertical == 0) {
            // 悬停要显式制动：只靠「不加加速度」时下降侧会一直带着残余速度下沉。
            self.setDeltaMovement(self.getDeltaMovement().add(0.0D,
                    FreedomFlightPolicy.hoverBrake(currentY) - currentY, 0.0D));
            return;
        }
        self.setDeltaMovement(self.getDeltaMovement().add(0.0D,
                FreedomFlightPolicy.verticalAccel(vertical, self.getAbilities().getFlyingSpeed()), 0.0D));
    }

    @Redirect(
            method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/Input;tick(ZF)V"),
            require = 1)
    private void wenxingtools$freedomFlightIgnoreSlowdown(Input input, boolean movingSlowly, float friction) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        input.tick(FreedomFlightPolicy.slowdownApplies(movingSlowly,
                FreedomFlightService.isFlyingOn(self)), friction);
    }
}
