package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.integration.TaczIntegration;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.UUID;

public final class AmpSupport {

    private static final String RESOURCE_AMPLIFICATION_SWITCH_CD_TAG =
            com.wenxing.wenxingtools.WenXingTools.MODID + ":resource_amp_switch_cd";

    private AmpSupport() {
    }

    public static boolean isResourceAmplificationEnabled(Player player) {
        if (player == null) {
            return false;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.contains(AmpConstants.RESOURCE_AMPLIFICATION_PLAYER_TAG)) {
            return false;
        }
        return data.getBoolean(AmpConstants.RESOURCE_AMPLIFICATION_PLAYER_TAG);
    }

    public static void setResourceAmplificationEnabled(Player player, boolean enabled) {
        if (player == null) {
            return;
        }
        player.getPersistentData().putBoolean(AmpConstants.RESOURCE_AMPLIFICATION_PLAYER_TAG, enabled);
    }

    public static int computeResourceFactor(int level) {
        return level * 100;
    }

    public static Component buildResourceAmplificationToggleMessage(boolean newState, int displayLevel) {
        int factor = computeResourceFactor(displayLevel);
        String base;
        ChatFormatting color;
        if (newState) {
            color = ChatFormatting.GREEN;
            if (displayLevel > 0) {

                int totalFactor = factor + 1;
                base = "资源增幅附魔：已开启（当前资源倍率: " + totalFactor + "，经验倍率: " + (displayLevel * 10000) + "）";
            } else {
                base = "资源增幅附魔：已开启";
            }
        } else {
            color = ChatFormatting.RED;
            base = "资源增幅附魔：已关闭";
        }
        return Component.literal(base).withStyle(color);
    }


    public static void syncPlayerAmplifications(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }

        int totalAttackLevel = getTotalEnchantmentLevelAllSlots(player, ModEnchantments.ATTACK_AMPLIFICATION.get());
        player.getPersistentData().putInt(AmpConstants.ATTACK_AMPLIFICATION_LEVEL_TAG, totalAttackLevel);


        int totalLifeLevel = getTotalEnchantmentLevelOnArmor(player, ModEnchantments.LIFE_AMPLIFICATION.get());
        LifeAmplificationEvents.syncLifeAmplification(player, totalLifeLevel);
    }


    public static void reinitializePlayerAmplifications(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        clearLifeAmplificationLevelState(player.getUUID());
        clearPotionAmplificationPlayerState(player.getUUID());
        syncPlayerAmplifications(player);
        // 切维/重生/登录后客户端 LocalPlayer 重建，强制重发夜视/幸运，避免 addEffect 无变更不发包
        LifeAmplificationEvents.forceResyncLifeAmplificationEffects(player);
    }


    public static void clearPotionAmplificationPlayerState(UUID playerId) {
        PotionAmplificationEvents.clearPotionAmplificationPlayerState(playerId);
    }


    public static void clearLifeAmplificationLevelState(UUID playerId) {
        LifeAmplificationEvents.clearLifeAmplificationLevelState(playerId);
    }

    public static int getTotalEnchantmentLevelAllSlots(Player player, Enchantment enchantment) {
        int totalLevel = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
            }
        }
        return totalLevel;
    }


    public static Player resolvePlayerAttacker(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) {
            return null;
        }
        Entity causing = source.getEntity();
        if (causing instanceof Player player) {
            return player;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
            Entity owner = projectile.getOwner();
            if (owner instanceof Player player) {
                return player;
            }
        }
        if (TaczIntegration.isTaczBulletEntity(direct)
                && direct instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
            Entity owner = projectile.getOwner();
            if (owner instanceof Player player) {
                return player;
            }
        }
        return null;
    }


    public static int resolveAttackAmplificationLevel(Player player) {
        if (player == null) {
            return 0;
        }
        int live = getTotalEnchantmentLevelAllSlots(player, ModEnchantments.ATTACK_AMPLIFICATION.get());
        player.getPersistentData().putInt(AmpConstants.ATTACK_AMPLIFICATION_LEVEL_TAG, live);
        return live;
    }


    public static int resolveEnchantLevel(Player player, Enchantment enchantment) {
        if (player == null || enchantment == null) {
            return 0;
        }
        int level = EnchantmentHelper.getItemEnchantmentLevel(enchantment, player.getMainHandItem());
        if (level > 0) {
            return level;
        }
        return getTotalEnchantmentLevelAllSlots(player, enchantment);
    }

    public static int getTotalEnchantmentLevelOnArmor(Player player, Enchantment enchantment) {
        int totalLevel = 0;
        for (EquipmentSlot slot : AmpConstants.ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            totalLevel += EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
        }
        return totalLevel;
    }

    public static int getEffectiveResourceAmplificationLevel(Player player, ItemStack stack) {
        if (!isResourceAmplificationEnabled(player)) {
            return 0;
        }
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.RESOURCE_AMPLIFICATION.get(), stack);
    }


    public static int resolveResourceLevel(Player player, Entity directEntity) {
        if (directEntity != null && TaczIntegration.isTaczBulletEntity(directEntity)) {
            int level = TaczIntegration.getResourceAmplificationLevelFromBullet(directEntity);
            return level > 0 && isResourceAmplificationEnabled(player) ? level : 0;
        }
        return getEffectiveResourceAmplificationLevel(player, player.getMainHandItem());
    }

    public static boolean tryToggleResourceAmplificationByPlayer(Player player, ItemStack contextStack) {
        if (player == null || player.level().isClientSide) {
            return false;
        }
        if (contextStack != null && !contextStack.isEmpty()) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.RESOURCE_AMPLIFICATION.get(), contextStack);
            if (level <= 0) {

                player.displayClientMessage(
                        Component.literal("主手物品没有资源增幅附魔")
                                .withStyle(ChatFormatting.RED),
                        true);
                return false;
            }
            long now = System.currentTimeMillis();
            long lastSwitch = player.getPersistentData().getLong(RESOURCE_AMPLIFICATION_SWITCH_CD_TAG);
            if (now - lastSwitch < AmpConstants.RESOURCE_AMPLIFICATION_SWITCH_COOLDOWN_MS) {
                return false;
            }
            player.getPersistentData().putLong(RESOURCE_AMPLIFICATION_SWITCH_CD_TAG, now);
            boolean newState = !isResourceAmplificationEnabled(player);
            setResourceAmplificationEnabled(player, newState);
            player.displayClientMessage(buildResourceAmplificationToggleMessage(newState, level), true);
            return true;
        }
        return false;
    }
}
