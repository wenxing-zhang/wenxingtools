package com.wenxing.wenxingtools.entity;

import com.wenxing.wenxingtools.capability.AuthorityDataProvider;
import com.wenxing.wenxingtools.capability.IAuthorityData;
import com.wenxing.wenxingtools.network.PacketHandler;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 自由飞行状态的开关与回收。服务端权威——状态变更与同步只在这里做。
 * 飞行态存在玩家权威 Capability 上，随既有权威数据同步包下发客户端镜像。
 */
public final class FreedomFlightService {
    private FreedomFlightService() {}

    /** 飞行中贴地累计刻数：只在服务端主线程访问（玩家 tick 与网络包均已入主线程），故用 HashMap。 */
    private static final Map<UUID, Integer> GROUND_TICKS = new HashMap<>();

    public static boolean isFlyingOn(Player player) {
        IAuthorityData data = dataOf(player);
        return data != null && data.isFreedomFlying();
    }

    /** 双击触发的开关：飞行中则收起，否则校验开关后起飞。 */
    public static boolean toggle(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (isFlyingOn(player)) {
            stopFlight(player);
            return true;
        }
        return startFlight(player);
    }

    public static boolean startFlight(ServerPlayer player) {
        if (player == null || isFlyingOn(player)) {
            return false;
        }
        IAuthorityData data = dataOf(player);
        if (data == null || !data.isFreedomCharacteristicOn()) {
            return false;
        }
        applyFlying(player, data, true);
        return true;
    }

    /** 开关关闭 / 落地 / 失权 / 登出 / 死亡克隆 / 重生 / 切维时的统一回收出口。 */
    public static void stopFlight(Player player) {
        if (player == null) {
            return;
        }
        GROUND_TICKS.remove(player.getUUID());
        IAuthorityData data = dataOf(player);
        if (data != null && data.isFreedomFlying()) {
            applyFlying(player, data, false);
        }
    }

    /** 每刻维护：失权自动收起；贴地满 5 刻收起。 */
    public static void maintainFlight(ServerPlayer player, IAuthorityData data) {
        if (player == null || data == null) {
            return;
        }
        UUID id = player.getUUID();
        if (!data.isFreedomFlying()) {
            GROUND_TICKS.remove(id);
            return;
        }
        if (!PermissionUtil.isFreedomCharacteristicActiveOrRevoke(player, data)) {
            stopFlight(player);
            return;
        }
        boolean touchingGround = isTouchingGround(player);
        boolean rising = player.getDeltaMovement().y > 0.0D;
        int ticks = FreedomFlightPolicy.countsAsGrounded(true, touchingGround, rising)
                ? GROUND_TICKS.getOrDefault(id, 0) + 1
                : 0;
        if (FreedomFlightPolicy.shouldStopOnGround(true, touchingGround, rising, ticks)) {
            stopFlight(player);
            return;
        }
        GROUND_TICKS.put(id, ticks);
    }

    /** 贴地判定：玩家包围盒底面（即实体 y）下方探 0.08 格是否有碰撞。 */
    public static boolean isTouchingGround(Player player) {
        if (player == null) {
            return false;
        }
        return !player.level().noCollision(player,
                player.getBoundingBox().move(0.0D, -FreedomFlightPolicy.GROUND_PROBE, 0.0D));
    }

    public static void clearPlayerState(UUID playerId) {
        if (playerId != null) {
            GROUND_TICKS.remove(playerId);
        }
    }

    // 客户端镜像与服务端权威共用同一 Capability：不能用只收 ServerPlayer 的 getData
    private static IAuthorityData dataOf(Player player) {
        if (player == null) {
            return null;
        }
        return player.getCapability(AuthorityDataProvider.AUTHORITY_DATA).orElse(null);
    }

    private static void applyFlying(Player player, IAuthorityData data, boolean flying) {
        data.setFreedomFlying(flying);
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.level().isClientSide) {
            PacketHandler.syncAuthorityData(serverPlayer);
        }
    }
}
