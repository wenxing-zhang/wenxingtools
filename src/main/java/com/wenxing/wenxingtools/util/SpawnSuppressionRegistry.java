package com.wenxing.wenxingtools.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SpawnSuppressionRegistry {

    private static final ConcurrentHashMap<ResourceKey<Level>, DimensionState> DIMENSIONS = new ConcurrentHashMap<>();
    private static final AtomicInteger TORCH_COUNT = new AtomicInteger();

    private SpawnSuppressionRegistry() {
    }

    public static void register(ServerLevel level, ChunkPos center) {
        DimensionState state = DIMENSIONS.computeIfAbsent(level.dimension(), key -> new DimensionState());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                state.chunks.merge(ChunkPos.asLong(center.x + dx, center.z + dz), 1, Integer::sum);
            }
        }
        state.torches.incrementAndGet();
        TORCH_COUNT.incrementAndGet();
    }

    public static void unregister(ServerLevel level, ChunkPos center) {
        DimensionState state = DIMENSIONS.get(level.dimension());
        if (state != null) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    state.chunks.compute(ChunkPos.asLong(center.x + dx, center.z + dz),
                            (key, count) -> count == null || count <= 1 ? null : count - 1);
                }
            }
            state.torches.updateAndGet(current -> current > 0 ? current - 1 : 0);
            // 全局计数与维度状态配对扣减：若该维度已被 clearDimension 清理（state 为 null），
            // 残留火把的全局配额已在卸载时扣过，此处不得再扣——否则全局计数可能跌到 0，
            // 使 isSuppressed 的快速路径对所有维度返回 false，其它维度的火把抑制静默失效。
            TORCH_COUNT.updateAndGet(current -> current > 0 ? current - 1 : 0);
        }
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
