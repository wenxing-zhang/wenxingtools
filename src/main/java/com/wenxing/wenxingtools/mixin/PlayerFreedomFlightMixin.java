package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.util.MixinMarkers;
import com.wenxing.wenxingtools.entity.FreedomFlightService;
import com.wenxing.wenxingtools.util.MixinSelfCheck;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 自由飞行：把原版创造飞行判定所读的「是否飞行中」改写到自有状态上。
 * <p>
 * 原版创造飞行的两条主干都以 {@code abilities.flying} 为条件——水平速度取自
 * {@code getFlyingSpeed()}、垂直衰减与免坠落取自 {@code travel()} 的飞行分支。
 * 改写该字段的读取即可整条复用原版飞行代码路径，而不去写 Abilities 字段本身
 * （写该字段会被接管玩家能力数据的模组在下次能力同步时覆写掉）。
 * <p>
 * 1.21.1 中 {@code Player.travel} 与 {@code Player.getFlyingSpeed} 各只有一处
 * {@code Abilities.flying} 字段读取（已对照反编译源），故 {@code require = 1} 精确命中。
 * priority 取大值（本仓库约定：数值越大越后应用、越能覆盖），且读值保留了原字段真值，
 * 因此不影响其它模组基于 {@code abilities.flying} 的飞行能力。
 */
@Mixin(value = Player.class, priority = 1500)
public abstract class PlayerFreedomFlightMixin implements MixinMarkers.FreedomFlightTravel {

    @Redirect(
            method = "travel",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightTravelGate(Abilities abilities) {
        if (!MixinSelfCheck.isFreedomFlightEnabled()) {
            return abilities.flying;
        }
        return abilities.flying || FreedomFlightService.isFlyingOn((Player) (Object) this);
    }

    @Redirect(
            method = "getFlyingSpeed",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightSpeedGate(Abilities abilities) {
        if (!MixinSelfCheck.isFreedomFlightEnabled()) {
            return abilities.flying;
        }
        return abilities.flying || FreedomFlightService.isFlyingOn((Player) (Object) this);
    }
}
