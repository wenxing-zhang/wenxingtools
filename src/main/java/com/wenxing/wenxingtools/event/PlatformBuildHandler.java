package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.entity.MultiStackItemEntity;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 平台生成任务队列：把 48×48×2 填充 + 54×54 两段清除摊到多个服务端 tick 上执行，
 * 单 tick 上限 {@link #CELLS_PER_TICK} 格，避免在山体/地下触发时一次性改写数十万方块卡死主线程。
 * <p>
 * 容器内容由原版 {@code onRemove} / 潜影盒战利品表的 {@code set_contents} 结算，这里只补普通方块的战利品——
 * 自行导出容器槽位会与上面两条路径重复，导致内容物掉两份。
 */
@EventBusSubscriber(modid = WenXingTools.MODID)
public final class PlatformBuildHandler {
    private static final int CELLS_PER_TICK = 6000;
    private static final int DROP_BATCH_ENTRIES = 512;
    private static final List<Job> JOBS = new ArrayList<>();

    private PlatformBuildHandler() {
    }

    /** @return false 表示该区域已有同锚点的任务在跑（连点木棍不会叠加任务） */
    public static boolean submit(ServerLevel level, ChunkPos center, int anchorY, UUID playerId) {
        for (Job job : JOBS) {
            if (job.level == level && job.center.equals(center) && job.anchorY == anchorY) {
                return false;
            }
        }
        JOBS.add(new Job(level, center, anchorY, playerId));
        return true;
    }

    public static boolean isBusy(ServerLevel level) {
        for (Job job : JOBS) {
            if (job.level == level) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (JOBS.isEmpty()) {
            return;
        }
        int budget = CELLS_PER_TICK;
        int i = 0;
        while (i < JOBS.size() && budget > 0) {
            Job job = JOBS.get(i);
            budget -= job.step(budget);
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
        private final UUID playerId;
        private final PlatformGenerator.Cursor cursor;
        private final Map<DropStackUtil.StackKey, Long> totals = new LinkedHashMap<>();
        private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        private int filled;
        private int fillReplaced;
        private int ringCleared;
        private int aboveCleared;
        private boolean finished;

        private Job(ServerLevel level, ChunkPos center, int anchorY, UUID playerId) {
            this.level = level;
            this.center = center;
            this.anchorY = anchorY;
            this.playerId = playerId;
            this.cursor = new PlatformGenerator.Cursor(center.x, center.z, anchorY, level.getMaxBuildHeight());
        }

        private int step(int budget) {
            int processed = 0;
            while (processed < budget && cursor.advance()) {
                processed++;
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
                    case RING -> {
                        if (occupied) {
                            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                            ringCleared++;
                        }
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
            if (cursor.phase() == PlatformGenerator.Phase.DONE) {
                finished = true;
            }
            return processed;
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
                player.displayClientMessage(Component.literal("wenxing平台已生成：48×48×2，破坏 "
                        + (fillReplaced + ringCleared + aboveCleared) + " 格"), true);
            }
        }

        private void popDrops() {
            List<ItemStack> stacks = DropStackUtil.totalsToStacks(totals);
            if (stacks.isEmpty()) {
                return;
            }
            double x = PlatformGeometry.minX(center.x) + PlatformGeometry.SIDE / 2.0;
            double y = PlatformGeometry.topY(anchorY) + 1.5;
            double z = PlatformGeometry.minZ(center.z) + PlatformGeometry.SIDE / 2.0;
            for (int i = 0; i < stacks.size(); i += DROP_BATCH_ENTRIES) {
                List<ItemStack> batch = new ArrayList<>(
                        stacks.subList(i, Math.min(i + DROP_BATCH_ENTRIES, stacks.size())));
                level.addFreshEntity(MultiStackItemEntity.fromStacks(level, x, y, z, batch));
            }
            totals.clear();
        }
    }
}
