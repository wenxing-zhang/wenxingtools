package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.util.MixinMarkers;
import com.wenxing.wenxingtools.entity.FreedomFlightPolicy;
import com.wenxing.wenxingtools.entity.FreedomFlightService;
import com.wenxing.wenxingtools.util.MixinSelfCheck;
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
 * 少改写任何一处，自由飞行就会在该处退回「不在飞行中」的行为：位移主干与水平速度、
 * 潜行防掉边、潜行姿势、入水切游泳、受水流推动、方块减速、方块黏滞、移动音效
 * （寻声守卫/幽匿感测器）。因此按同一不变量统一接管，语义集中到
 * {@link FreedomFlightPolicy#effectiveFlying}。
 * <p>
 * 不写 {@code Abilities} 字段本身（会被接管玩家能力数据的模组在下次能力同步时覆写），
 * 读值保留原字段真值，因此不影响其它模组基于 {@code abilities.flying} 的飞行能力。
 * <p>
 * 故意不改写的读取点只剩一处：{@code aiStep}——双击跳跃切换飞行、落地自动关飞行等原版开关逻辑。
 * 该类内读 {@code abilities.flying} 的方法经字节码核对共 12 个、每个恰 1 次读取，11 处已接管、余下即此 1 处。
 * （「肩上实体移除判定」曾列为待取舍点，但该方法并不读该字段，不属于消费点。）
 * <p>
 * 1.21.1 与 1.20.1 的差异：灵魂疾行已改为数据驱动（{@code Enchantments.SOUL_SPEED} 的
 * 属性修饰条件只判方块标签），{@code Player} 内不再有读 {@code abilities.flying} 的
 * {@code onSoulSpeedBlock} / {@code shouldRemoveSoulSpeed}，故无对应注入点；
 * 客户端另有 {@code isSuppressingSlidingDownLadder} / {@code canSpawnSprintParticle} /
 * FOV 三处只影响视觉与贴梯滑动，与 1.20.1 同口径不接管。
 */
@Mixin(value = Player.class, priority = 1500)
public abstract class PlayerFreedomFlightMixin implements MixinMarkers.FreedomFlightTravel {

    @Redirect(
            method = "travel",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightTravelGate(Abilities abilities) {
        return wenxingtools$flyingOrFreedom(abilities);
    }

    @Redirect(
            method = "getFlyingSpeed",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightSpeedGate(Abilities abilities) {
        return wenxingtools$flyingOrFreedom(abilities);
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
        return wenxingtools$flyingOrFreedom(abilities);
    }

    /** 潜行姿势：原版飞行中按潜行不进蹲姿（碰撞箱不变矮），自由飞行同样如此。 */
    @Redirect(
            method = "updatePlayerPose",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightPoseGate(Abilities abilities) {
        return wenxingtools$flyingOrFreedom(abilities);
    }

    /** 游泳状态：原版飞行中强制 setSwimming(false)，否则入水会被切成游泳（碰撞箱骤缩、速度变化）。 */
    @Redirect(
            method = "updateSwimming",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightSwimGate(Abilities abilities) {
        return wenxingtools$flyingOrFreedom(abilities);
    }

    /** 液体影响：原版飞行中不受液体阻力/浮力/下沉（潜行键在流体里不会触发下沉）。 */
    @Redirect(
            method = "isAffectedByFluids",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightFluidGate(Abilities abilities) {
        return wenxingtools$flyingOrFreedom(abilities);
    }

    /** 液体推动：原版飞行中不被水流/岩浆流推着走。 */
    @Redirect(
            method = "isPushedByFluid",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightFluidPushGate(Abilities abilities) {
        return wenxingtools$flyingOrFreedom(abilities);
    }

    /** 方块减速：原版飞行中不踩灵魂沙/水的减速系数（恒为 1.0）。 */
    @Redirect(
            method = "getBlockSpeedFactor",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightBlockSpeedGate(Abilities abilities) {
        return wenxingtools$flyingOrFreedom(abilities);
    }

    /** 游泳判定：原版飞行中不算游泳（与 updateSwimming 配套）。 */
    @Redirect(
            method = "isSwimming",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightSwimmingStateGate(Abilities abilities) {
        return wenxingtools$flyingOrFreedom(abilities);
    }

    /** 方块黏滞：原版飞行中不会被甜浆果/细雪/蛛网黏住（原式为 {@code !flying}）。 */
    @Redirect(
            method = "makeStuckInBlock",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightStuckGate(Abilities abilities) {
        return wenxingtools$flyingOrFreedom(abilities);
    }

    /** 移动动静：原版飞行中不产生移动音效与游戏事件（寻声守卫/幽匿感测不触发）。 */
    @Redirect(
            method = "getMovementEmission",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z"),
            require = 1)
    private boolean wenxingtools$freedomFlightMovementEmissionGate(Abilities abilities) {
        return wenxingtools$flyingOrFreedom(abilities);
    }

    /** 自检关闭时回落到字段原值，注入本身仍在，不会因开关而让注入点打空。 */
    @Unique
    private boolean wenxingtools$flyingOrFreedom(Abilities abilities) {
        if (!MixinSelfCheck.isFreedomFlightEnabled()) {
            return abilities.flying;
        }
        return FreedomFlightPolicy.effectiveFlying(abilities.flying,
                FreedomFlightService.isFlyingOn((Player) (Object) this));
    }
}
