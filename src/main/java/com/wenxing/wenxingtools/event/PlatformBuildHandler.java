package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.entity.MultiStackItemEntity;
import com.wenxing.wenxingtools.util.ChunkScope;
import com.wenxing.wenxingtools.util.DropStackUtil;
import com.wenxing.wenxingtools.util.PlatformGenerator;
import com.wenxing.wenxingtools.util.PlatformGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 平台生成任务队列：把「填充平台层 + 清除正上方 16 层」摊到多个服务端 tick 上执行。
 *
 * <p>三重闸门：单 tick 最多 {@link #CELLS_PER_TICK} 格、最多 {@link #TICK_BUDGET_NANOS} 处理时间、
 * 区块预热每 tick 最多 {@link #CHUNK_LOADS_PER_TICK} 个——15×15 档涉及 225 个区块与约 104 万格，
 * 任一维度不设限都会把卡顿集中到单 tick。
 *
 * <p>容器内容由原版 {@code onRemove} / 潜影盒战利品表结算，这里只补普通方块的战利品。
 */
public final class PlatformBuildHandler {
    private static final int CELLS_PER_TICK = 6000;
    private static final long TICK_BUDGET_NANOS = 8_000_000L;
    private static final int CHUNK_LOADS_PER_TICK = 2;
    private static final int DROP_BATCH_ENTRIES = 512;
    private static final List<Job> JOBS = new ArrayList<>();

    private PlatformBuildHandler() {
    }

    /** @return false 表示该区域已有同锚点的任务在跑（连点木棍不会叠加任务） */
    public static boolean submit(ServerLevel level, ChunkPos center, int anchorY, ChunkScope scope, UUID playerId) {
        for (Job job : JOBS) {
            if (job.level == level && job.center.equals(center) && job.anchorY == anchorY) {
                return false;
            }
        }
        JOBS.add(new Job(level, center, anchorY, scope, playerId));
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) {
            return;
        }
        int budget = CELLS_PER_TICK;
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
        int i = 0;
        while (i < JOBS.size() && budget > 0 && System.nanoTime() < deadline) {
            Job job = JOBS.get(i);
            budget -= job.step(budget, deadline);
            if (job.finished) {
                job.finish();
                JOBS.remove(i);
            } else {
                i++;
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            JOBS.removeIf(job -> job.level == level);
        }
    }

    private static final class Job {
        private final ServerLevel level;
        private final ChunkPos center;
        private final int anchorY;
        private final ChunkScope scope;
        private final UUID playerId;
        private final PlatformGenerator.Cursor cursor;
        private final Map<DropStackUtil.StackKey, Long> totals = new LinkedHashMap<>();
        private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        private final int warmupSide;
        private final int warmupTotal;
        private int warmupIndex;
        private int filled;
        private int fillReplaced;
        private int aboveCleared;
        private boolean finished;

        private Job(ServerLevel level, ChunkPos center, int anchorY, ChunkScope scope, UUID playerId) {
            this.level = level;
            this.center = center;
            this.anchorY = anchorY;
            this.scope = scope;
            this.playerId = playerId;
            this.cursor = new PlatformGenerator.Cursor(scope, center.x, center.z, anchorY, level.getMaxBuildHeight());
            this.warmupSide = scope.radiusChunks() * 2 + 1;
            this.warmupTotal = warmupSide * warmupSide;
        }

        private int step(int budget, long deadline) {
            if (warmupIndex < warmupTotal) {
                return warmUp(deadline);
            }
            int processed = 0;
            while (processed < budget && System.nanoTime() < deadline && cursor.advance()) {
                processCell();
                processed++;
            }
            if (cursor.phase() == PlatformGenerator.Phase.DONE) {
                finished = true;
            }
            return processed;
        }

        /** 区块预热：先把本档涉及的区块加载完，避免逐格 setBlock 时同步加载造成单 tick 长卡 */
        private int warmUp(long deadline) {
            int loaded = 0;
            int baseChunkX = center.x - scope.radiusChunks();
            int baseChunkZ = center.z - scope.radiusChunks();
            while (warmupIndex < warmupTotal
                    && loaded < CHUNK_LOADS_PER_TICK
                    && System.nanoTime() < deadline) {
                int idx = warmupIndex++;
                level.getChunk(baseChunkX + idx / warmupSide, baseChunkZ + idx % warmupSide);
                loaded++;
            }
            return 0;
        }

        private void processCell() {
            at.set(cursor.x(), cursor.y(), cursor.z());
            boolean occupied = harvest(at);
            switch (cursor.phase()) {
                case FILL -> {
                    if (occupied) {
                        fillReplaced++;
                    }
                    level.setBlock(at, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    filled++;
                }
                case ABOVE -> {
                    if (occupied) {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        aboveCleared++;
                    }
                }
                default -> {
                }
            }
        }

        /** 按原方块结算战利品；容器内容不在这里补（原版 onRemove / 潜影盒战利品表已带走） */
        private boolean harvest(BlockPos pos) {
            BlockState old = level.getBlockState(pos);
            if (old.isAir()) {
                return false;
            }
            BlockEntity blockEntity = old.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            List<ItemStack> drops = Block.getDrops(old, level, pos, blockEntity);
            if (!drops.isEmpty()) {
                DropStackUtil.mergeStacksMultiplied(drops, 1L)
                        .forEach((key, amount) -> totals.merge(key, amount, Long::sum));
            }
            return true;
        }

        private void finish() {
            popDrops();
            MinecraftServer server = level.getServer();
            ServerPlayer player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.displayClientMessage(Component.literal("wenxing平台已生成：" + scope.label()
                        + "区块，破坏 " + (fillReplaced + aboveCleared) + " 格"), false);
            }
        }

        private void popDrops() {
            List<ItemStack> stacks = DropStackUtil.totalsToStacks(totals);
            if (stacks.isEmpty()) {
                return;
            }
            double x = PlatformGeometry.minX(center.x, scope) + scope.blocks() / 2.0;
            double y = PlatformGeometry.topY(anchorY) + 1.5;
            double z = PlatformGeometry.minZ(center.z, scope) + scope.blocks() / 2.0;
            for (int i = 0; i < stacks.size(); i += DROP_BATCH_ENTRIES) {
                List<ItemStack> batch = new ArrayList<>(
                        stacks.subList(i, Math.min(i + DROP_BATCH_ENTRIES, stacks.size())));
                level.addFreshEntity(MultiStackItemEntity.fromStacks(level, x, y, z, batch));
            }
            totals.clear();
        }
    }
}
