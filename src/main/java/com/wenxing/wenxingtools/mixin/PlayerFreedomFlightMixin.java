package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.entity.FreedomFlightPolicy;
import com.wenxing.wenxingtools.entity.FreedomFlightService;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 自由飞行：把原版「是否飞行中」的全部消费点改写到自有状态上。
 * <p>
 * 原版创造飞行的语义分散在十几个消费点，它们读的是同一个 {@code abilities.flying}。
 * 少改写任何一处，自由飞行就会在该处退回「不在飞行中」的行为：位移分支与水平速度、
 * 潜行防掉边、潜行姿势、入水切游泳、受水流推动、方块减速、灵魂疾行、方块黏滞、
 * 移动音效（寻声守卫/sculk 感知）。因此按同一不变量统一接管，语义集中到
 * {@link FreedomFlightPolicy#effectiveFlying}。
 * <p>
 * 不写 {@code Abilities} 字段本身（会被接管玩家能力数据的模组在下次能力同步时覆写），
 * 读值保留原字段真值，因此不影响其它模组基于 {@code abilities.flying} 的飞行能力。
 * <p>
 * 故意不改写的读取点：双击跳跃切换飞行、落地自动关飞行（原版开关逻辑）、
 * 肩上实体的移除判定（{@code removeEntitiesOnShoulder}，只在肩上有实体时才有动作，
 * 与飞行手感无关，不值得为一个无害偏差增加注入面）。
 */
@Mixin(value = Player.class, priority = 1500)
public abstract class PlayerFreedomFlightMixin {

    @Redirect(
            method = "travel",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightTravelGate(Abilities abilities) {
        return flyingOrFreedom(abilities);
    }

    @Redirect(
            method = "getFlyingSpeed",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightSpeedGate(Abilities abilities) {
        return flyingOrFreedom(abilities);
    }

    /**
     * 潜行防掉边：原版对「不在飞行中、向下移动、按住潜行、贴地」的玩家收窄水平移动，
     * 避免站在方块边缘潜行时掉下去。飞行中按潜行是在下降，这条收窄必须跳过。
     */
    @Redirect(
            method = "maybeBackOffFromEdge",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightEdgeGuardGate(Abilities abilities) {
        return flyingOrFreedom(abilities);
    }

    /** 潜行姿势：原版飞行中按潜行不进蹲姿（碰撞箱不变矮），自由飞行同样如此。 */
    @Redirect(
            method = "updatePlayerPose",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightPoseGate(Abilities abilities) {
        return flyingOrFreedom(abilities);
    }

    /** 游泳状态：原版飞行中强制 setSwimming(false)，否则入水会被切成游泳（碰撞箱骤缩、速度变化）。 */
    @Redirect(
            method = "updateSwimming",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightSwimGate(Abilities abilities) {
        return flyingOrFreedom(abilities);
    }

    /** 液体影响：原版飞行中不受液体阻力/浮力/下沉（潜行键在流体里不会触发下沉）。 */
    @Redirect(
            method = "isAffectedByFluids",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightFluidGate(Abilities abilities) {
        return flyingOrFreedom(abilities);
    }

    /** 液体推动：原版飞行中不被水流/岩浆流推着走。 */
    @Redirect(
            method = "isPushedByFluid",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightFluidPushGate(Abilities abilities) {
        return flyingOrFreedom(abilities);
    }

    /** 方块减速：原版飞行中不踩灵魂沙/水的减速系数（恒为 1.0）。 */
    @Redirect(
            method = "getBlockSpeedFactor",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightBlockSpeedGate(Abilities abilities) {
        return flyingOrFreedom(abilities);
    }

    /** 游泳判定：原版飞行中不算游泳（与 updateSwimming 配套）。 */
    @Redirect(
            method = "isSwimming",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightSwimmingStateGate(Abilities abilities) {
        return flyingOrFreedom(abilities);
    }

    /** 灵魂疾行触发：原版飞行中不在灵魂沙上触发（原式为 {@code !flying && super}）。 */
    @Redirect(
            method = "onSoulSpeedBlock",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightSoulSpeedGate(Abilities abilities) {
        return flyingOrFreedom(abilities);
    }

    /** 灵魂疾行移除：原式是 {@code flying || super}，飞行中应移除该附魔效果。 */
    @Redirect(
            method = "shouldRemoveSoulSpeed",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightSoulSpeedRemoveGate(Abilities abilities) {
        return flyingOrFreedom(abilities);
    }

    /** 方块黏滞：原版飞行中不会被甜浆果/细雪/蛛网黏住（原式为 {@code !flying}）。 */
    @Redirect(
            method = "makeStuckInBlock",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightStuckGate(Abilities abilities) {
        return flyingOrFreedom(abilities);
    }

    /** 移动动静：原版飞行中不产生移动音效与游戏事件（寻声守卫/幽匿感测不触发）。 */
    @Redirect(
            method = "getMovementEmission",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightMovementEmissionGate(Abilities abilities) {
        return flyingOrFreedom(abilities);
    }

    @Unique
    private boolean flyingOrFreedom(Abilities abilities) {
        return FreedomFlightPolicy.effectiveFlying(abilities.flying,
                FreedomFlightService.isFlyingOn((Player) (Object) this));
    }
}
