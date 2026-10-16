package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.entity.FreedomFlightService;
import com.wenxing.wenxingtools.util.MixinSelfCheck;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自由飞行不写 {@code Abilities.mayfly}，拿不到原版「悬空太久」检测的豁免名额
 * （豁免名单只有 允许飞行 / 飘浮 / 鞘翅 / 旋转攻击 / 旁观 / 乘骑 / 服务端 allow-flight）。
 * 该检测累计 80 刻即断开连接，这里在每刻开头清零两个计数——只对确实处于自由飞行的玩家生效。
 * <p>
 * 置于 common 段而非 server 段：单人游戏的逻辑服务端跑在客户端进程内，Mixin 的 side 是 CLIENT，
 * 放 server 段会导致「悬空太久」检测在单人游戏下不被豁免（1.20.1 即为此分列方式）。
 * <p>
 * 1.21.1 的 {@code player} 字段是 {@code public} 且非 final（重生时会被重新赋值），
 * 故 @Shadow 不能用 {@code private @Final}——那样会与目标字段修饰符不匹配而注入失败。
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 1500)
public abstract class ServerFreedomFlightGuardMixin implements MixinMarkers.FreedomFlightGuard {

    @Shadow
    public ServerPlayer player;

    @Shadow
    private boolean clientIsFloating;

    @Shadow
    private int aboveGroundTickCount;

    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void wenxingtools$exemptFreedomFlightFromFloatingCheck(CallbackInfo ci) {
        if (!MixinSelfCheck.isFreedomFlightEnabled()) {
            return;
        }
        if (FreedomFlightService.isFlyingOn(this.player)) {
            this.clientIsFloating = false;
            this.aboveGroundTickCount = 0;
        }
    }
}
