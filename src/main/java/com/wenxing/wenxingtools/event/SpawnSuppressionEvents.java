package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.block.CustomSpawnerBlockEntity;
import com.wenxing.wenxingtools.util.SpawnSuppressionRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.EnumSet;
import java.util.Set;

@EventBusSubscriber(modid = WenXingTools.MODID)
public class SpawnSuppressionEvents {

    // 放行清单只收玩家主动放出的生物（刷怪蛋 / 发射器 / 桶 / 繁殖）与既有实体的转化（治愈、变异）。
    // 其余来源（自然、区块生成、原版与试炼刷怪笼、结构、巡逻、袭击、增援、骑手、指令召唤）仍属「刷怪」，一律禁止。
    private static final Set<MobSpawnType> ALLOWED_SPAWN_TYPES = EnumSet.of(
            MobSpawnType.SPAWN_EGG,
            MobSpawnType.DISPENSER,
            MobSpawnType.BUCKET,
            MobSpawnType.BREEDING,
            MobSpawnType.CONVERSION
    );

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (SpawnSuppressionRegistry.isSuppressed(event.getLevel().getLevel(), event.getX(), event.getZ())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
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

    // 文星刷怪笼的产物自带标记：该路径不调 finalizeSpawn，getSpawnType 恒为 null，只能靠标记区分；
    // 其余未经 finalizeSpawn 的实体（Mod 自行构造后直接入世）同样为 null，按「刷怪」处理。
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
