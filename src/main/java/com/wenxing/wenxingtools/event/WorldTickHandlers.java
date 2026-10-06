package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.attachment.AuthorityAttachments;
import com.wenxing.wenxingtools.attachment.IAuthorityData;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = WenXingTools.MODID)
public final class WorldTickHandlers {
    private WorldTickHandlers() {}

    private static final double KILL_AURA_RADIUS = 10.0;
    private static final int KILL_AURA_INTERVAL_TICKS = 20;
    private static final int KILL_AURA_MAX_TARGETS = 100;
    private static int killAuraTickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        boolean runKillAuraThisTick = killAuraTickCounter == 0;
        killAuraTickCounter = (killAuraTickCounter + 1) % KILL_AURA_INTERVAL_TICKS;
        if (!runKillAuraThisTick) {
            return;
        }
        // 无人在线时跳过全维度扫描
        if (server.getPlayerCount() <= 0) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            var players = level.players();
            if (players.isEmpty()) {
                continue;
            }
            for (ServerPlayer player : players) {
                IAuthorityData data = AuthorityAttachments.getData(player);
                if (data == null || !data.isKillAuraCharacteristicOn()) {
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
        if (target == null || target == player) {
            return false;
        }
        if (!target.isAlive()) {
            return false;
        }
        if (target instanceof Player) {
            return false;
        }
        if (!target.isAttackable()) {
            return false;
        }
        if (Math.abs(target.getX() - player.getX()) > KILL_AURA_RADIUS) {
            return false;
        }
        if (Math.abs(target.getZ() - player.getZ()) > KILL_AURA_RADIUS) {
            return false;
        }
        if (target.getType() == EntityType.ARMOR_STAND) {
            return false;
        }
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
        // XZ 按半径扩展，Y 限定 ±2（对齐 1.20.1，避免竖向空扫）
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(KILL_AURA_RADIUS, 2.0, KILL_AURA_RADIUS));

        int killed = 0;
        for (LivingEntity target : candidates) {
            if (killed >= KILL_AURA_MAX_TARGETS) {
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
