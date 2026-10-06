package com.wenxing.wenxingtools.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class EnchantmentEvents {

    public static boolean tryToggleResourceAmplificationByPlayer(Player player, ItemStack contextStack) {
        return AmpSupport.tryToggleResourceAmplificationByPlayer(player, contextStack);
    }

    public static void reinitializePlayerAmplifications(Player player) {
        AmpSupport.reinitializePlayerAmplifications(player);
    }

    public static void clearPotionAmplificationPlayerState(UUID playerId) {
        AmpSupport.clearPotionAmplificationPlayerState(playerId);
    }

    public static void clearLifeAmplificationLevelState(UUID playerId) {
        AmpSupport.clearLifeAmplificationLevelState(playerId);
    }
}
