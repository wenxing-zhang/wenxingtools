package com.wenxing.wenxingtools.entity;

import com.wenxing.wenxingtools.capability.AuthorityDataProvider;
import com.wenxing.wenxingtools.capability.IAuthorityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 飞行座的生成与回收。服务端权威——生成、下马、丢弃都只在这里做。
 */
public final class FlightSeatService {
    private FlightSeatService() {}

    /** 载具的维持条件：自由特性开着且未潜行。抽成纯函数便于隔离验证。 */
    public static boolean shouldReclaim(boolean freedomOn, boolean sneaking) {
        return sneaking || !freedomOn;
    }

    public static boolean isFreedomOn(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        IAuthorityData data = AuthorityDataProvider.getData(player);
        return data != null && data.isFreedomCharacteristicOn();
    }

    /** 双击触发的开关：已乘座则下马回收，否则校验开关后召唤。 */
    public static boolean toggle(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.getVehicle() instanceof FlightSeatEntity seat) {
            dismount(player, seat);
            return true;
        }
        return summon(player);
    }

    public static boolean summon(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!isFreedomOn(player)) {
            return false;
        }
        if (player.getVehicle() instanceof FlightSeatEntity) {
            return false;
        }
        FlightSeatEntity seat = ModEntities.FLIGHT_SEAT.get().create(level);
        if (seat == null) {
            return false;
        }
        seat.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        if (!level.addFreshEntity(seat)) {
            return false;
        }
        player.startRiding(seat, true);
        return true;
    }

    /** 开关关闭 / 登出 / 死亡克隆 / 重生 / 切维时的统一回收出口。 */
    public static void dismountAndDiscard(Player player) {
        if (player == null) {
            return;
        }
        if (player.getVehicle() instanceof FlightSeatEntity seat) {
            dismount(player, seat);
        }
        FlightSeatInputStore.clear(player.getUUID());
    }

    private static void dismount(Player player, FlightSeatEntity seat) {
        player.stopRiding();
        if (!seat.isRemoved()) {
            seat.discard();
        }
    }
}
