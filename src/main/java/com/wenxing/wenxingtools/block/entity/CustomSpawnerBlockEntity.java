package com.wenxing.wenxingtools.block.entity;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

public class CustomSpawnerBlockEntity extends BlockEntity {

    public static final int SPAWN_DELAY_TICKS = 20;
    public static final int SPAWN_COUNT = 16;
    public static final int MAX_NEARBY_ENTITIES = 32;
    public static final int SPAWN_RANGE = 8;

    public static int delayTicks() {
        return SPAWN_DELAY_TICKS;
    }

    public static int spawnCount() {
        return SPAWN_COUNT;
    }

    public static int maxNearbyEntities() {
        return MAX_NEARBY_ENTITIES;
    }

    public static int spawnRange() {
        return SPAWN_RANGE;
    }


    public static final String SPAWNER_MOB_TAG = WenXingTools.MODID + ":spawner_mob";

    private static final int GROUND_SEARCH_BELOW = 2;
    private static final int GROUND_SEARCH_ABOVE = 2;

    private int cooldown = delayTicks();
    private EntityType<?> spawnType = EntityType.ZOMBIE;

    public CustomSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CUSTOM_SPAWNER.get(), pos, state);
    }

    public void setSpawnType(EntityType<?> type) {
        if (type != null && type != this.spawnType) {
            this.spawnType = type;
            this.setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public EntityType<?> getSpawnType() {
        return spawnType;
    }

    public boolean isPowered() {
        BlockState state = getBlockState();
        return state.hasProperty(com.wenxing.wenxingtools.block.CustomSpawnerBlock.POWERED)
                && state.getValue(com.wenxing.wenxingtools.block.CustomSpawnerBlock.POWERED);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(spawnType);
        if (key != null) {
            tag.putString("SpawnType", key.toString());
        }
        tag.putInt("Cooldown", cooldown);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("SpawnType")) {
            ResourceLocation key = ResourceLocation.tryParse(tag.getString("SpawnType"));
            if (key != null) {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(key);
                if (type != null) {
                    this.spawnType = type;
                }
            }
        }
        if (tag.contains("Cooldown")) {
            this.cooldown = Math.max(0, tag.getInt("Cooldown"));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }


    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.load(tag);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CustomSpawnerBlockEntity be) {
        if (!state.getValue(com.wenxing.wenxingtools.block.CustomSpawnerBlock.POWERED)) {
            return;
        }
        if (be.cooldown > 0) {
            be.cooldown--;
            return;
        }
        be.cooldown = delayTicks();
        be.tickSpawn(level, pos);
    }


    public static void clientTick(Level level, BlockPos pos, BlockState state, CustomSpawnerBlockEntity be) {
        if (!state.getValue(com.wenxing.wenxingtools.block.CustomSpawnerBlock.POWERED)) {
            return;
        }
        RandomSource random = level.getRandom();
        if (random.nextFloat() < 0.35F) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble() * 0.8 + 0.1;
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0D, 0.0D, 0.0D);
        }
        if (random.nextFloat() < 0.15F) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble() * 0.6 + 0.2;
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.02D, 0.0D);
        }
    }

    private void tickSpawn(Level level, BlockPos pos) {
        if (spawnType == null) {
            return;
        }

        AABB countArea = new AABB(pos).inflate(spawnRange());
        int nearby = level.getEntitiesOfClass(LivingEntity.class, countArea,
                e -> e.getType() == spawnType && e.isAlive()).size();
        if (nearby >= maxNearbyEntities()) {
            return;
        }

        RandomSource random = level.getRandom();
        int toSpawn = Math.min(spawnCount(), maxNearbyEntities() - nearby);
        boolean anySpawned = false;
        for (int i = 0; i < toSpawn; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = 1.0 + random.nextDouble() * (double) (Math.max(1, spawnRange() - 1));
            double x = pos.getX() + 0.5 + Math.cos(angle) * dist;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * dist;

            Entity entity = spawnType.create(level);
            if (!(entity instanceof Mob mob)) {
                if (entity != null) {
                    entity.discard();
                }
                continue;
            }

            if (!(level instanceof ServerLevel serverLevel)) {
                mob.discard();
                continue;
            }


            Double groundY = findStandableY(serverLevel, x, z, mob);
            if (groundY == null) {
                mob.discard();
                continue;
            }
            mob.moveTo(x, groundY, z, random.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(mob.blockPosition()),
                    MobSpawnType.SPAWNER, null, null);

            mob.setNoAi(true);
            mob.setNoGravity(false);
            mob.setPersistenceRequired();
            mob.getPersistentData().putBoolean(SPAWNER_MOB_TAG, true);
            if (!level.noCollision(mob) || !level.isUnobstructed(mob)) {
                mob.discard();
                continue;
            }

            if (!serverLevel.tryAddFreshEntityWithPassengers(mob)) {
                continue;
            }
            mob.spawnAnim();
            anySpawned = true;
        }

        if (anySpawned) {
            level.levelEvent(LevelEvent.PARTICLES_MOBBLOCK_SPAWN, pos, 0);
        }
    }


    private static Double findStandableY(ServerLevel level, double x, double z, Mob mob) {
        int ix = Mth.floor(x);
        int iz = Mth.floor(z);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
        for (int dy = GROUND_SEARCH_ABOVE; dy >= -GROUND_SEARCH_BELOW; dy--) {
            int y = surface + dy;
            if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                continue;
            }
            BlockPos feet = new BlockPos(ix, y, iz);

            BlockPos below = feet.below();
            if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                continue;
            }

            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                continue;
            }
            mob.moveTo(x + 0.5, y, z + 0.5, mob.getYRot(), 0.0F);
            if (!level.noCollision(mob) || !level.isUnobstructed(mob)) {
                continue;
            }
            return (double) y;
        }
        return null;
    }
}
