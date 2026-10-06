package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.capability.AuthorityDataProvider;
import com.wenxing.wenxingtools.capability.IAuthorityData;
import com.wenxing.wenxingtools.util.CombatUtil;
import com.wenxing.wenxingtools.util.ModEnums;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

public final class WorldTickHandlers {
    private WorldTickHandlers() {}


    private static final double KILL_AURA_RADIUS = 10.0;
    private static final int KILL_AURA_INTERVAL_TICKS = 20;
    private static final int KILL_AURA_MAX_TARGETS = 100;
    private static int killAuraTickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = event.getServer();
        boolean runKillAuraThisTick = (killAuraTickCounter % KILL_AURA_INTERVAL_TICKS) == 0;
        killAuraTickCounter++;
        if (killAuraTickCounter > Integer.MAX_VALUE / 4) {
            killAuraTickCounter = 0;
        }



        if (!runKillAuraThisTick) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                IAuthorityData data = AuthorityDataProvider.getData(player);
                if (data == null) {
                    continue;
                }

                if (!data.isKillAuraCharacteristicOn()) {
                    continue;
                }
                boolean hasPermission = PermissionUtil.hasPermission(player);
                if (PermissionUtil.isKillAuraCharacteristicActiveOrRevoke(player, data, hasPermission)) {
                    runKillAuraForPlayer(server, level, player, data.getKillAuraMode());
                }
            }
        }
    }

    private static boolean testKillAuraTarget(ServerPlayer player, boolean hostileOnly, LivingEntity target) {
        if (target == null || target == player) return false;
        if (!target.isAlive()) return false;
        if (target instanceof Player) return false;
        if (!target.isAttackable()) return false;

        if (Math.abs(target.getX() - player.getX()) > KILL_AURA_RADIUS) return false;
        if (Math.abs(target.getZ() - player.getZ()) > KILL_AURA_RADIUS) return false;
        if (target.getType() == EntityType.ARMOR_STAND) return false;
        if (hostileOnly) {
            return target instanceof Enemy
                    || target.getType().getCategory() == MobCategory.MONSTER;
        }
        return true;
    }

    private static void runKillAuraForPlayer(MinecraftServer server, ServerLevel level, ServerPlayer player, int mode) {
        if (player.isDeadOrDying() || !player.isAlive()) {
            return;
        }

        boolean hostileOnly = ModEnums.KillAuraMode.fromPersistentId(mode) == ModEnums.KillAuraMode.HOSTILE;
        double radius = KILL_AURA_RADIUS;
        int maxTargets = KILL_AURA_MAX_TARGETS;

        AABB searchBox = new AABB(
                player.getX() - radius, player.getY() - 2.0D, player.getZ() - radius,
                player.getX() + radius, player.getY() + 2.0D, player.getZ() + radius);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox);

        int killed = 0;
        for (LivingEntity target : candidates) {
            if (killed >= maxTargets) {
                break;
            }
            if (!testKillAuraTarget(player, hostileOnly, target)) {
                continue;
            }
            CombatUtil.forceKillEntityByCommandChain(server, target, player);
            killed++;
        }
    }
}
