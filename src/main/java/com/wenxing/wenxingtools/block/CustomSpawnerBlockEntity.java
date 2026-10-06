package com.wenxing.wenxingtools.block;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.item.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * 文星刷怪笼 BE（行为对齐 Forge 1.20.1）：
 * - 硬编码：延迟 20t / 单次 16 / 附近上限 32 / 刷怪范围 8
 * - 红石通电才刷；忽略群系/光照；不做玩家在场检测
 * - NoAI + 持久化；出生点吸附可站立地面（±2）
 * - 客户端：供电时火焰/烟雾粒子
 */
public class CustomSpawnerBlockEntity extends BlockEntity {
    public static final int SPAWN_DELAY_TICKS = 20;
    public static final int SPAWN_COUNT = 16;
    public static final int MAX_NEARBY_ENTITIES = 32;
    public static final int SPAWN_RANGE = 8;

    private static final String TAG_SPAWN_TYPE = "SpawnType";
    private static final String TAG_COOLDOWN = "Cooldown";
    private static final String SPAWNER_MOB_TAG = WenXingTools.MODID + ":spawner_mob";
    private static final int GROUND_SEARCH_BELOW = 2;
    private static final int GROUND_SEARCH_ABOVE = 2;

    private int cooldown = SPAWN_DELAY_TICKS;
    @Nullable
    private ResourceLocation entityTypeKey;
    /** 缓存红石状态，避免每 tick 写方块状态 */
    private boolean cachedPowered;

    public CustomSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CUSTOM_SPAWNER_BE.get(), pos, state);
    }

    public void setSpawnType(@Nullable EntityType<?> type) {
        ResourceLocation key = type == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(type);
        ResourceLocation old = entityTypeKey;
        if (key != null && !key.equals(old)) {
            this.entityTypeKey = key;
            this.setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Nullable
    public EntityType<?> getSpawnType() {
        if (entityTypeKey == null) { return null; } return BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeKey).orElse(null);
    }

    public boolean isPowered() {
        BlockState state = getBlockState();
        return state.hasProperty(CustomSpawnerBlock.POWERED)
                && state.getValue(CustomSpawnerBlock.POWERED);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (entityTypeKey != null) {
            tag.putString(TAG_SPAWN_TYPE, entityTypeKey.toString());
        }
        tag.putInt(TAG_COOLDOWN, cooldown);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_SPAWN_TYPE)) {
            ResourceLocation key = ResourceLocation.tryParse(tag.getString(TAG_SPAWN_TYPE));
            entityTypeKey = key;
        } else {
            entityTypeKey = null;
        }
        cooldown = tag.getInt(TAG_COOLDOWN);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CustomSpawnerBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // 红石：通电 = 工作（对齐 1.20.1 POWERED 语义）
        boolean powered = serverLevel.hasNeighborSignal(pos);
        if (powered != be.cachedPowered) {
            be.cachedPowered = powered;
            if (state.hasProperty(CustomSpawnerBlock.POWERED) && state.getValue(CustomSpawnerBlock.POWERED) != powered) {
                serverLevel.setBlock(pos, state.setValue(CustomSpawnerBlock.POWERED, powered), 2);
            }
        }
        if (!powered) {
            return;
        }
        if (be.cooldown > 0) {
            be.cooldown--;
            be.setChanged();
            return;
        }
        be.cooldown = SPAWN_DELAY_TICKS;
        be.setChanged();
        be.tickSpawn(serverLevel, pos);
    }

    /** 客户端：供电时播火焰/烟雾粒子。 */
    public static void clientTick(Level level, BlockPos pos, BlockState state, CustomSpawnerBlockEntity be) {
        if (!state.hasProperty(CustomSpawnerBlock.POWERED) || !state.getValue(CustomSpawnerBlock.POWERED)) {
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

    private void tickSpawn(ServerLevel level, BlockPos pos) {
        EntityType<?> spawnType = getSpawnType();
        if (spawnType == null) {
            return;
        }
        AABB countArea = new AABB(pos).inflate(SPAWN_RANGE);
        int nearby = level.getEntitiesOfClass(LivingEntity.class, countArea,
                e -> e.getType() == spawnType && e.isAlive()).size();
        if (nearby >= MAX_NEARBY_ENTITIES) {
            return;
        }

        RandomSource random = level.getRandom();
        int toSpawn = Math.min(SPAWN_COUNT, MAX_NEARBY_ENTITIES - nearby);
        for (int i = 0; i < toSpawn; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = 1.0 + random.nextDouble() * (double) (Math.max(1, SPAWN_RANGE - 1));
            double x = pos.getX() + 0.5 + Math.cos(angle) * dist;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * dist;

            Entity entity = spawnType.create(level);
            if (!(entity instanceof Mob mob)) {
                if (entity != null) {
                    entity.discard();
                }
                continue;
            }

            Double groundY = findStandableY(level, x, z, mob);
            if (groundY == null) {
                mob.discard();
                continue;
            }
            mob.moveTo(x, groundY, z, random.nextFloat() * 360.0F, 0.0F);
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.getPersistentData().putBoolean(SPAWNER_MOB_TAG, true);
            if (!level.addFreshEntity(mob)) {
                mob.discard();
            }
        }
        setChanged();
    }

    @Nullable
    private static Double findStandableY(ServerLevel level, double x, double z, Mob mob) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        for (int dy = GROUND_SEARCH_ABOVE; dy >= -GROUND_SEARCH_BELOW; dy--) {
            int y = ground + dy;
            BlockPos feet = new BlockPos(bx, y, bz);
            if (!level.isInWorldBounds(feet) || !level.isLoaded(feet)) {
                continue;
            }
            BlockPos below = feet.below();
            if (level.getBlockState(below).isAir()) {
                continue;
            }
            mob.moveTo(x, y, z, 0.0F, 0.0F);
            if (level.noCollision(mob)) {
                return (double) y;
            }
        }
        return null;
    }
}
