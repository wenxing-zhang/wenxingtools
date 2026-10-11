package com.wenxing.wenxingtools.util;

import com.wenxing.wenxingtools.capability.IAuthorityData;
import com.wenxing.wenxingtools.network.PacketHandler;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

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
        return isActiveOrRevoke(player, data, hasPermission,
                IAuthorityData::isInvincibleCharacteristicOn,
                IAuthorityData::setInvincibleCharacteristic);
    }

    public static boolean isResourceCharacteristicActiveOrRevoke(ServerPlayer player, IAuthorityData data, boolean hasPermission) {
        return isActiveOrRevoke(player, data, hasPermission,
                IAuthorityData::isResourceCharacteristicOn,
                IAuthorityData::setResourceCharacteristic);
    }

    public static boolean isFreedomCharacteristicActiveOrRevoke(ServerPlayer player, IAuthorityData data, boolean hasPermission) {
        return isActiveOrRevoke(player, data, hasPermission,
                IAuthorityData::isFreedomCharacteristicOn,
                IAuthorityData::setFreedomCharacteristic);
    }

    public static boolean isKillAuraCharacteristicActiveOrRevoke(ServerPlayer player, IAuthorityData data, boolean hasPermission) {
        return isActiveOrRevoke(player, data, hasPermission,
                IAuthorityData::isKillAuraCharacteristicOn,
                IAuthorityData::setKillAuraCharacteristic);
    }

    // 四个特性共用的「开启且有权则放行，开着但失权则关掉并推同步」判定。
    // 方法引用均无捕获，无额外分配。
    private static boolean isActiveOrRevoke(ServerPlayer player, IAuthorityData data, boolean hasPermission,
                                            Predicate<IAuthorityData> isOn,
                                            CharacteristicFlagSetter setOff) {
        if (player == null || data == null) return false;
        if (!isOn.test(data)) return false;
        if (!hasPermission) {
            setOff.set(data, false);
            syncAfterRevoke(player);
            return false;
        }
        return true;
    }

    @FunctionalInterface
    private interface CharacteristicFlagSetter {
        void set(IAuthorityData data, boolean active);
    }

    // 失权翻转后立即推一次权威数据，避免客户端镜像停留在旧开关值
    private static void syncAfterRevoke(ServerPlayer player) {
        if (player.level().isClientSide) {
            return;
        }
        PacketHandler.syncAuthorityData(player);
    }
}
