package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.entity.FreedomFlightService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自由飞行不写 {@code Abilities.mayfly}，拿不到原版「悬空太久」检测的豁免名额
 * （豁免名单只有 允许飞行 / 飘浮 / 鞘翅 / 旋转攻击 / 旁观 / 乘骑 / 服务端 allow-flight）。
 * 该检测累计 80 刻即断开连接，这里在每刻开头清零两个计数——只对确实处于自由飞行的玩家生效。
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 1500)
public abstract class ServerFreedomFlightGuardMixin {

    @Shadow @Final private ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private int aboveGroundTickCount;

    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void wenxingtools$exemptFreedomFlightFromFloatingCheck(CallbackInfo ci) {
        if (FreedomFlightService.isFlyingOn(this.player)) {
            this.clientIsFloating = false;
            this.aboveGroundTickCount = 0;
        }
    }
}
