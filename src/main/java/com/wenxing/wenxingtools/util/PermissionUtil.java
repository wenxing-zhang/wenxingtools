package com.wenxing.wenxingtools.util;

import com.wenxing.wenxingtools.attachment.IAuthorityData;
import net.minecraft.server.level.ServerPlayer;

public final class PermissionUtil {
    private PermissionUtil() {}

    public static boolean hasPermission(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.connection == null) {
            return false;
        }
        if (WhitelistManager.contains(player.getUUID())) {
            return true;
        }
        return player.hasPermissions(2);
    }

    public static boolean isInvincibleCharacteristicActiveOrRevoke(ServerPlayer player, IAuthorityData data, boolean hasPermission) {
        if (player == null || data == null) {
            return false;
        }
        if (!data.isInvincibleCharacteristicOn()) {
            return false;
        }
        if (!hasPermission) {
            data.setInvincibleCharacteristic(false);
            return false;
        }
        return true;
    }

    public static boolean isResourceCharacteristicActiveOrRevoke(ServerPlayer player, IAuthorityData data, boolean hasPermission) {
        if (player == null || data == null) {
            return false;
        }
        if (!data.isResourceCharacteristicOn()) {
            return false;
        }
        if (!hasPermission) {
            data.setResourceCharacteristic(false);
            return false;
        }
        return true;
    }

    public static boolean isFreedomCharacteristicActiveOrRevoke(ServerPlayer player, IAuthorityData data, boolean hasPermission) {
        if (player == null || data == null) {
            return false;
        }
        if (!data.isFreedomCharacteristicOn()) {
            return false;
        }
        if (!hasPermission) {
            data.setFreedomCharacteristic(false);
            return false;
        }
        return true;
    }

    public static boolean isKillAuraCharacteristicActiveOrRevoke(ServerPlayer player, IAuthorityData data, boolean hasPermission) {
        if (player == null || data == null) {
            return false;
        }
        if (!data.isKillAuraCharacteristicOn()) {
            return false;
        }
        if (!hasPermission) {
            data.setKillAuraCharacteristic(false);
            return false;
        }
        return true;
    }
}
