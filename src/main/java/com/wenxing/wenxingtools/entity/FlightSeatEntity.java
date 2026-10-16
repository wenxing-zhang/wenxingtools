package com.wenxing.wenxingtools.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 飞行座：承载自由特性玩家的隐形载体。
 * 走「乘骑」通道而非 mayfly —— 原版悬空反作弊的豁免名单里，乘骑是唯一与 Abilities 无关的一条，
 * 而 mayfly / flying 会被接管玩家能力数据的模组整体覆写后留不住。
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
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

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
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
        if (!(getControllingPassenger() instanceof ServerPlayer rider) || !rider.isAlive()) {
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
