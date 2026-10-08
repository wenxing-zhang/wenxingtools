package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.util.SpawnSuppressionRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class SpawnSuppressionEvents {

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
        Entity entity = event.getEntity();
        if (entity instanceof Mob && SpawnSuppressionRegistry.isSuppressed(level, entity.getX(), entity.getZ())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SpawnSuppressionRegistry.clearDimension(level);
        }
    }
}
