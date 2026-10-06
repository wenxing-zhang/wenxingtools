package com.wenxing.wenxingtools.util;

import com.wenxing.wenxingtools.capability.IAuthorityData;
import com.wenxing.wenxingtools.network.PacketHandler;
import net.minecraft.server.level.ServerPlayer;

public final class PermissionUtil {
    private PermissionUtil() {}

    public static boolean hasPermission(ServerPlayer player) {
        if (player == null) return false;
        if (player.connection == null) return false;

        if (WhitelistManager.contains(player.getUUID())) return true;
        if (player.hasPermissions(2)) return true;

        return false;
    }

    public static boolean isInvincibleCharacteristicActiveOrRevoke(ServerPlayer player, IAuthorityData data, boolean hasPermission) {
        if (player == null || data == null) return false;
        if (!data.isInvincibleCharacteristicOn()) return false;
        if (!hasPermission) {
            data.setInvincibleCharacteristic(false);
            syncAfterRevoke(player);
            return false;
        }
        return true;
    }

    public static boolean isResourceCharacteristicActiveOrRevoke(ServerPlayer player, IAuthorityData data, boolean hasPermission) {
        if (player == null || data == null) return false;
        if (!data.isResourceCharacteristicOn()) return false;
        if (!hasPermission) {
            data.setResourceCharacteristic(false);
            syncAfterRevoke(player);
            return false;
        }
        return true;
    }

    public static boolean isFreedomCharacteristicActiveOrRevoke(ServerPlayer player, IAuthorityData data, boolean hasPermission) {
        if (player == null || data == null) return false;
        if (!data.isFreedomCharacteristicOn()) return false;
        if (!hasPermission) {
            data.setFreedomCharacteristic(false);
            syncAfterRevoke(player);
            return false;
        }
        return true;
    }

    public static boolean isKillAuraCharacteristicActiveOrRevoke(ServerPlayer player, IAuthorityData data, boolean hasPermission) {
        if (player == null || data == null) return false;
        if (!data.isKillAuraCharacteristicOn()) return false;
        if (!hasPermission) {
            data.setKillAuraCharacteristic(false);
            syncAfterRevoke(player);
            return false;
        }
        return true;
    }

    // 失权翻转后立即推一次权威数据，避免客户端镜像停留在旧开关值
    private static void syncAfterRevoke(ServerPlayer player) {
        if (player.level().isClientSide) {
            return;
        }
        PacketHandler.syncAuthorityData(player);
    }
}
