package com.wenxing.wenxingtools.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 飞行座：承载自由特性玩家的隐形载体。
 * 走「乘骑」通道而非 mayfly —— 原版悬空反作弊的豁免名单里，乘骑是唯一与 Abilities 无关的一条，
 * 而 mayfly / flying 会被接管玩家能力数据的模组（如 EndingLibrary）整体覆写后留不住。
 */
public class FlightSeatEntity extends Entity {

    private static final double SPEED = 0.42D;
    private static final double VERTICAL_RATIO = 0.8D;
    private static final double GROUND_PROBE = 0.08D;

    private boolean leftGround = false;
    private int groundTicks = 0;

    public FlightSeatEntity(EntityType<? extends FlightSeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    // 有意不覆写 getControllingPassenger()：它会让客户端 isControlledByLocalInstance() 判为 true，
    // 客户端遂丢弃本载具的位置包（ClientPacketListener.handleMoveEntity）、并把停滞的载具坐标回灌服务端
    // （LocalPlayer.tick 乘客分支），表现为「骑上去了但完全无法移动」。本载具位移由服务端 tick 驱动。
    @Nullable
    private ServerPlayer firstRider() {
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof ServerPlayer serverPlayer) {
                return serverPlayer;
            }
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        ServerPlayer rider = firstRider();
        if (rider == null || !rider.isAlive()) {
            this.discard();
            return;
        }
        if (!FlightSeatService.isFreedomOn(rider)) {
            FlightSeatService.dismountAndDiscard(rider);
            return;
        }

        FlightSeatInputStore.Input input = FlightSeatInputStore.get(rider.getUUID());
        boolean touchingGround = isTouchingGround();
        this.groundTicks = FlightSeatPolicy.countsAsGrounded(this.leftGround, touchingGround, input.up())
                ? this.groundTicks + 1
                : 0;
        if (FlightSeatPolicy.shouldDismountOnGround(this.leftGround, touchingGround, input.up(), this.groundTicks)) {
            FlightSeatService.dismountAndDiscard(rider);
            return;
        }
        if (!touchingGround) {
            this.leftGround = true;
        }

        Vec3 look = rider.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() > 1.0E-6D) {
            flat = flat.normalize();
        }
        Vec3 motion = flat.scale(input.forward() * SPEED)
                .add(0.0D, input.up() * SPEED * VERTICAL_RATIO, 0.0D);
        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);
        this.hurtMarked = true;
    }

    private boolean isTouchingGround() {
        return !this.level().noCollision(this, this.getBoundingBox().move(0.0D, -GROUND_PROBE, 0.0D));
    }
}
