package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.block.entity.CustomSpawnerBlockEntity;
import com.wenxing.wenxingtools.util.SpawnSuppressionRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.EnumSet;
import java.util.Set;

public class SpawnSuppressionEvents {

    // 放行清单只收玩家主动放出的生物（刷怪蛋 / 发射器 / 桶 / 繁殖 / 指令）与既有实体的转化（治愈、变异）。
    // 其余来源（自然、区块生成、原版刷怪笼、结构、巡逻、袭击、增援、骑手）仍属「刷怪」，一律禁止。
    private static final Set<MobSpawnType> ALLOWED_SPAWN_TYPES = EnumSet.of(
            MobSpawnType.SPAWN_EGG,
            MobSpawnType.DISPENSER,
            MobSpawnType.BUCKET,
            MobSpawnType.BREEDING,
            MobSpawnType.COMMAND,
            MobSpawnType.CONVERSION
    );

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (SpawnSuppressionRegistry.isSuppressed(event.getLevel().getLevel(), event.getX(), event.getZ())) {
            event.setResult(Event.Result.DENY);
        }
    }

    // loadedFromDisk 为真表示实体随区块加载重新入世，属「已存在的生物」，必须放行
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || isAllowedSource(mob)) {
            return;
        }
        if (SpawnSuppressionRegistry.isSuppressed(level, mob.getX(), mob.getZ())) {
            event.setCanceled(true);
        }
    }

    // 文星刷怪笼的产物自带标记，与原版刷怪笼共用 SPAWNER 类型，只能靠标记区分；
    // 未经 finalizeSpawn 的实体 getSpawnType 为 null（Mod 自行 new + addFreshEntity），按「刷怪」处理。
    private static boolean isAllowedSource(Mob mob) {
        if (mob.getPersistentData().getBoolean(CustomSpawnerBlockEntity.SPAWNER_MOB_TAG)) {
            return true;
        }
        MobSpawnType spawnType = mob.getSpawnType();
        return spawnType != null && ALLOWED_SPAWN_TYPES.contains(spawnType);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SpawnSuppressionRegistry.clearDimension(level);
        }
    }
}
