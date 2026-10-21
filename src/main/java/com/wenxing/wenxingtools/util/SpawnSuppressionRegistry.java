package com.wenxing.wenxingtools.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 火把刷怪抑制登记表：按「维度 + 区块」引用计数，范围随 {@link ChunkScope} 半径变化。
 * 判定只看 x/z（垂直方向不限，世界最低到最高全覆盖）。
 */
public final class SpawnSuppressionRegistry {

    private static final ConcurrentHashMap<ResourceKey<Level>, DimensionState> DIMENSIONS = new ConcurrentHashMap<>();
    private static final AtomicInteger TORCH_COUNT = new AtomicInteger();

    private SpawnSuppressionRegistry() {
    }

    public static void register(ServerLevel level, ChunkPos center, ChunkScope scope) {
        DimensionState state = DIMENSIONS.computeIfAbsent(level.dimension(), key -> new DimensionState());
        int radius = scope.radiusChunks();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                state.chunks.merge(ChunkPos.asLong(center.x + dx, center.z + dz), 1, Integer::sum);
            }
        }
        state.torches.incrementAndGet();
        TORCH_COUNT.incrementAndGet();
    }

    /** 必须传入登记时使用的同一 scope，否则减不干净（切档路径由 {@code WenxingTorchBlockEntity} 的簿记保证） */
    public static void unregister(ServerLevel level, ChunkPos center, ChunkScope scope) {
        DimensionState state = DIMENSIONS.get(level.dimension());
        if (state == null) {
            // 该维度已被 clearDimension 整体扣过全局计数；残留 BE 的注销请求必须忽略，
            // 否则全局计数被双减到 0，isSuppressed 的快速路径会把所有维度的火把一起静默关掉
            return;
        }
        int radius = scope.radiusChunks();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                state.chunks.compute(ChunkPos.asLong(center.x + dx, center.z + dz),
                        (key, count) -> count == null || count <= 1 ? null : count - 1);
            }
        }
        state.torches.updateAndGet(current -> current > 0 ? current - 1 : 0);
        TORCH_COUNT.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    public static boolean isSuppressed(ServerLevel level, double x, double z) {
        if (TORCH_COUNT.get() <= 0) {
            return false;
        }
        DimensionState state = DIMENSIONS.get(level.dimension());
        if (state == null) {
            return false;
        }
        return state.chunks.get(ChunkPos.asLong(Mth.floor(x) >> 4, Mth.floor(z) >> 4)) != null;
    }

    // 维度卸载时把该维度残留的火把一并从全局计数扣掉，否则快速路径会永久失效
    public static void clearDimension(ServerLevel level) {
        DimensionState state = DIMENSIONS.remove(level.dimension());
        if (state == null) {
            return;
        }
        int residual = state.torches.get();
        if (residual > 0) {
            TORCH_COUNT.updateAndGet(current -> Math.max(0, current - residual));
        }
    }

    private static final class DimensionState {
        final ConcurrentHashMap<Long, Integer> chunks = new ConcurrentHashMap<>();
        final AtomicInteger torches = new AtomicInteger();
    }
}
