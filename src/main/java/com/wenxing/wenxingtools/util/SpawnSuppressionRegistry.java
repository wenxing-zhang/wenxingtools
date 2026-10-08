package com.wenxing.wenxingtools.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SpawnSuppressionRegistry {

    private static final ConcurrentHashMap<ResourceKey<Level>, ConcurrentHashMap<Long, Integer>> CHUNK_COUNTS =
            new ConcurrentHashMap<>();
    private static final AtomicInteger TORCH_COUNT = new AtomicInteger();

    private SpawnSuppressionRegistry() {
    }

    public static void register(ServerLevel level, ChunkPos center) {
        ConcurrentHashMap<Long, Integer> chunks =
                CHUNK_COUNTS.computeIfAbsent(level.dimension(), key -> new ConcurrentHashMap<>());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunks.merge(ChunkPos.asLong(center.x + dx, center.z + dz), 1, Integer::sum);
            }
        }
        TORCH_COUNT.incrementAndGet();
    }

    public static void unregister(ServerLevel level, ChunkPos center) {
        ConcurrentHashMap<Long, Integer> chunks = CHUNK_COUNTS.get(level.dimension());
        if (chunks != null) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    chunks.compute(ChunkPos.asLong(center.x + dx, center.z + dz),
                            (key, count) -> count == null || count <= 1 ? null : count - 1);
                }
            }
            if (chunks.isEmpty()) {
                CHUNK_COUNTS.remove(level.dimension(), chunks);
            }
        }
        TORCH_COUNT.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    public static boolean isSuppressed(ServerLevel level, double x, double z) {
        if (TORCH_COUNT.get() <= 0) {
            return false;
        }
        ConcurrentHashMap<Long, Integer> chunks = CHUNK_COUNTS.get(level.dimension());
        if (chunks == null) {
            return false;
        }
        return chunks.get(ChunkPos.asLong(Mth.floor(x) >> 4, Mth.floor(z) >> 4)) != null;
    }

    public static void clearDimension(ServerLevel level) {
        CHUNK_COUNTS.remove(level.dimension());
    }
}
