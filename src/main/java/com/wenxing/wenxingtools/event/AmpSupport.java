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
        return level * AmpConstants.RESOURCE_FACTOR_STACKABLE_PER_LEVEL;
    }

    /**
     * 按物品分层的资源增幅额外倍率（**方块破坏路径**）：可堆叠物品 {@code level×100}，不可堆叠（{@code maxStackSize == 1}）{@code level×10}。
     * <p>
     * 不可堆叠物品若吃满 ×100，会在单个 MultiStack 实体里塞进上百个单件 ItemStack，NBT 与同步包体量失控，故单独降档。
     */
    public static int computeResourceFactorForStack(int level, ItemStack stack) {
        if (level <= 0 || stack == null || stack.isEmpty()) {
            return 0;
        }
        return level * (stack.getMaxStackSize() > 1
                ? AmpConstants.RESOURCE_FACTOR_STACKABLE_PER_LEVEL
                : AmpConstants.RESOURCE_FACTOR_UNSTACKABLE_PER_LEVEL);
    }

    /**
     * 击杀掉落的整体倍率：一次击杀捕获到的全部产物复制 {@link AmpConstants#KILL_DROP_COPIES} 份，
     * 每份再按物品是否可堆叠分层（可堆叠 {@code level×25}、不可堆叠 {@code level×5}），
     * 合计 {@code level×100} / {@code level×20}。
     * <p>
     * 无增幅（{@code level<=0}）返回 1，让原版掉落原样通过——击杀路径的「必合并」不依赖增幅开关。
     */
    public static long computeKillDropMultiplier(int level, ItemStack stack) {
        if (level <= 0 || stack == null || stack.isEmpty()) {
            return 1L;
        }
        long perCopy = (long) level * (stack.getMaxStackSize() > 1
                ? AmpConstants.KILL_FACTOR_STACKABLE_PER_COPY
                : AmpConstants.KILL_FACTOR_UNSTACKABLE_PER_COPY);
        return Math.min(perCopy * AmpConstants.KILL_DROP_COPIES, (long) Integer.MAX_VALUE);
    }

    public static Component buildResourceAmplificationToggleMessage(boolean newState, int displayLevel) {
        int factor = computeResourceFactor(displayLevel);
        String base;
        ChatFormatting color;
        if (newState) {
            color = ChatFormatting.GREEN;
            if (displayLevel > 0) {

                int totalFactor = factor + 1;
                int xpFactor = computeResourceFactor(displayLevel) * (int) AmpConstants.RESOURCE_XP_MULTIPLIER;
                base = "资源增幅附魔：已开启（当前资源倍率: " + totalFactor + "，经验倍率: " + xpFactor + "）";
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
        return sumEnchantmentLevel(player, enchantment, EquipmentSlot.values());
    }

    /**
     * 遍历给定槽位累加附魔等级。
     * <p>
     * 先过 {@code isEnchanted()}：1.20.1 的 {@code getItemEnchantmentLevel} 会解析整张附魔表构造 HashMap，
     * 对没有附魔数据的装备纯属白付一次解析与分配。
     */
    private static int sumEnchantmentLevel(Player player, Enchantment enchantment, EquipmentSlot[] slots) {
        int totalLevel = 0;
        for (EquipmentSlot slot : slots) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEnchanted()) {
                continue;
            }
            totalLevel += EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
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
        return live;
    }


    public static int resolveEnchantLevel(Player player, Enchantment enchantment) {
        if (player == null || enchantment == null) {
            return 0;
        }
        ItemStack mainHand = player.getMainHandItem();
        int level = mainHand.isEnchanted()
                ? EnchantmentHelper.getItemEnchantmentLevel(enchantment, mainHand)
                : 0;
        if (level > 0) {
            return level;
        }
        return getTotalEnchantmentLevelAllSlots(player, enchantment);
    }

    public static int getTotalEnchantmentLevelOnArmor(Player player, Enchantment enchantment) {
        return sumEnchantmentLevel(player, enchantment, AmpConstants.ARMOR_SLOTS);
    }

    public static int getEffectiveResourceAmplificationLevel(Player player, ItemStack stack) {
        if (!isResourceAmplificationEnabled(player)) {
            return 0;
        }
        if (stack == null || !stack.isEnchanted()) {
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
