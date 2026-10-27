package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.util.MixinMarkers;
import com.wenxing.wenxingtools.entity.FreedomFlightPolicy;
import com.wenxing.wenxingtools.entity.FreedomFlightService;
import com.wenxing.wenxingtools.util.MixinSelfCheck;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自由飞行的垂直输入：复刻原版创造飞行在 {@code LocalPlayer.aiStep} 内的那段
 * （跳跃上升 / 潜行下降，每次把 {@code j * flyingSpeed * 3.0} 累加到垂直速度）。
 * <p>
 * 原版那段自身以 {@code abilities.flying} 为条件，但该方法内共有 10 处读该字段
 * （字节码核对：创造模式双击切换、鞘翅起跳、落地自动关飞行等），逐处改写会误伤原版逻辑；
 * 因此这里不改写读取，而是独立注入到 {@code super.aiStep()} 调用点之前，自己判自有状态。
 * <p>
 * crouching 字段同理：原版在该字段的赋值行读 {@code abilities.flying}，自由飞行不写该能力字段，
 * 于是自由飞行时 crouching 仍为真（创造飞行时为假）。这里不在赋值行改读取点（同处 ordinal
 * 定位脆弱且会误伤原版开关），改为在 {@code aiStep} 收尾统一置假——不触碰任何读取点。
 */
@Mixin(value = LocalPlayer.class, priority = 1500)
public abstract class LocalPlayerFreedomFlightMixin implements MixinMarkers.FreedomFlightClient {

    @Shadow
    private boolean crouching;

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
        if (!MixinSelfCheck.isFreedomFlightEnabled()) {
            return;
        }
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

    /**
     * crouching 与创造飞行对齐：自由飞行中按住潜行键不应让 crouching 为真。
     * 原版创造飞行靠 crouching 恒假让「潜行减速 / 潜行姿势 / 第三方 isCrouching 判定」自然失效，
     * 自由飞行不写 abilities 字段就拿不到这个副作用，只能在收尾显式置假。
     * <p>
     * 放在 RETURN 而非赋值行：父类 {@code Player.aiStep} 的 crouching 赋值先执行完，此处统一修正，
     * 因此不必知道赋值发生在父类还是本类、也不依赖任何 ordinal。
     * 置假后 {@code isMovingSlowly()} 自然为假，下方慢速系数重定向成为冗余保险——保留它是因为
     * 自检降级路径下本注入不生效时仍需那一层，避免按住潜行下降就只剩三成水平速度。
     */
    @Inject(method = "aiStep", at = @At("RETURN"), require = 1)
    private void wenxingtools$freedomFlightClearCrouching(CallbackInfo ci) {
        if (!MixinSelfCheck.isFreedomFlightEnabled()) {
            return;
        }
        if (FreedomFlightService.isFlyingOn((LocalPlayer) (Object) this)) {
            this.crouching = false;
        }
    }

    /**
     * 慢速系数：原版 {@code aiStep} 用 {@code input.tick(isMovingSlowly(), f)} 把水平输入乘成 30%。
     * crouching 已被上面的收尾注入置假，正常情况下 {@code movingSlowly} 本就是假；
     * 这一层是冗余保险，用于 crouching 修正未生效时仍不吃 30% 减速。
     * <p>
     * 不按 ordinal 去改 crouching 的赋值：{@code aiStep} 内读 {@code abilities.flying} 共 10 处
     * （双击切换、鞘翅起跳、落地关飞行等，字节码核对），序号定位脆弱且会误伤原版开关逻辑。
     */
    @Redirect(
            method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/Input;tick(ZF)V"),
            require = 1)
    private void wenxingtools$freedomFlightIgnoreSlowdown(Input input, boolean movingSlowly, float friction) {
        if (!MixinSelfCheck.isFreedomFlightEnabled()) {
            input.tick(movingSlowly, friction);
            return;
        }
        LocalPlayer self = (LocalPlayer) (Object) this;
        input.tick(FreedomFlightPolicy.slowdownApplies(movingSlowly,
                FreedomFlightService.isFlyingOn(self)), friction);
    }
}
