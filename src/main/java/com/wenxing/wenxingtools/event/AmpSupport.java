package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.integration.TaczIntegration;
import com.wenxing.wenxingtools.util.AmpRules;
import com.wenxing.wenxingtools.util.AttackerKind;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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

    /** 开关读物品：跟着物品走，女仆与假玩家手持即可生效，不再依赖玩家身上的状态。 */
    public static boolean isResourceAmpEnabled(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(AmpConstants.RESOURCE_AMPLIFICATION_ITEM_TAG);
    }

    public static void setResourceAmpEnabled(ItemStack stack, boolean enabled) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.getOrCreateTag().putBoolean(AmpConstants.RESOURCE_AMPLIFICATION_ITEM_TAG, enabled);
    }

    public static int computeResourceFactor(int level) {
        if (level <= 0) {
            return 0;
        }
        return (int) Math.min((long) level * (long) AmpConstants.RESOURCE_FACTOR_STACKABLE_PER_LEVEL,
                (long) Integer.MAX_VALUE);
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
        int perLevel = stack.getMaxStackSize() > 1
                ? AmpConstants.RESOURCE_FACTOR_STACKABLE_PER_LEVEL
                : AmpConstants.RESOURCE_FACTOR_UNSTACKABLE_PER_LEVEL;
        return (int) Math.min((long) level * (long) perLevel, (long) Integer.MAX_VALUE);
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

    public static Component buildResourceAmplificationToggleMessage(boolean newState) {
        return Component.literal(newState ? "资源增幅附魔：已开启" : "资源增幅附魔：已关闭")
                .withStyle(newState ? ChatFormatting.GREEN : ChatFormatting.RED);
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
        player.getPersistentData().remove(AmpConstants.RESOURCE_AMPLIFICATION_PLAYER_TAG);
        clearLifeAmplificationLevelState(player.getUUID());
        clearPotionAmplificationPlayerState(player.getUUID());
        syncPlayerAmplifications(player);
        PotionAmplificationEvents.cleanseHarmfulEffectsIfWorn(player);
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
        if (direct instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

    /**
     * 解析攻击者并分类，解析不出来时回退目标自记的击杀者。
     * <p>
     * 与 {@link #resolvePlayerAttacker} 的差别：这里接受任意生物（女仆、其他模组的召唤物、Forge 假玩家），
     * 附魔链路用它；需要真实玩家身份的权限/无敌特性路径仍用 {@code resolvePlayerAttacker}。
     */
    public static Attacker resolveAttacker(net.minecraft.world.damagesource.DamageSource source) {
        LivingEntity resolved = resolveAttackerEntity(source);
        return resolved == null ? null : new Attacker(resolved, classifyAttacker(resolved));
    }

    /**
     * 击杀归属：伤害源解析不出时回退目标自记的击杀者。
     * <p>
     * <b>只用于死亡与掉落链。</b>命中链不得回退——否则「玩家打过、别的实体收尾」的伤害会被算成玩家攻击，
     * 攻击增幅与回响会在错误来源上多打一次真伤。
     */
    public static Attacker resolveKillAttacker(net.minecraft.world.damagesource.DamageSource source, LivingEntity target) {
        Attacker attacker = resolveAttacker(source);
        if (attacker != null) {
            return attacker;
        }
        LivingEntity credit = target == null ? null : target.getKillCredit();
        return credit == null ? null : new Attacker(credit, classifyAttacker(credit));
    }

    private static LivingEntity resolveAttackerEntity(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) {
            return null;
        }
        Entity causing = source.getEntity();
        if (causing instanceof LivingEntity living) {
            return living;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof LivingEntity owner) {
            return owner;
        }
        return null;
    }

    /**
     * 真实玩家判据：能在这个 ServerPlayer 自己的服务端玩家列表里查到它。
     * <p>
     * 不用 {@code instanceof FakePlayer}——模组自建的 ServerPlayer 子类同样应算假玩家；
     * 也不用 {@code connection == null}——Forge 的 FakePlayer 会给自己塞一个 FakePlayerNetHandler。
     */
    public static AttackerKind classifyAttacker(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return AttackerKind.MOB;
        }
        boolean inPlayerList = false;
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.getServer() != null) {
            inPlayerList = serverPlayer.getServer().getPlayerList().getPlayer(serverPlayer.getUUID()) == serverPlayer;
        }
        return AmpRules.classify(true, inPlayerList);
    }

    /**
     * 装备槽等级取最大。
     * <p>
     * 遍历全部槽位而不是只看主手：副手与护甲槽同样算。对生物而言这四个护甲槽
     * 就是它的装备栏，女仆的武器在主手槽即可读到。
     */
    public static int maxEnchantmentLevel(LivingEntity entity, Enchantment enchantment) {
        if (entity == null || enchantment == null) {
            return 0;
        }
        EquipmentSlot[] slots = EquipmentSlot.values();
        int[] levels = new int[slots.length];
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = entity.getItemBySlot(slots[i]);
            if (!stack.isEnchanted()) {
                continue;
            }
            levels[i] = EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
        }
        return AmpRules.max(levels);
    }

    public static int resolveAttackAmplificationLevel(Attacker attacker) {
        if (attacker == null) {
            return 0;
        }
        return maxEnchantmentLevel(attacker.entity(), ModEnchantments.ATTACK_AMPLIFICATION.get());
    }

    public static int resolveEnchantLevel(Attacker attacker, Enchantment enchantment) {
        if (attacker == null || enchantment == null) {
            return 0;
        }
        return maxEnchantmentLevel(attacker.entity(), enchantment);
    }

    public static int getTotalEnchantmentLevelOnArmor(Player player, Enchantment enchantment) {
        return sumEnchantmentLevel(player, enchantment, AmpConstants.ARMOR_SLOTS);
    }

    public static int getEffectiveResourceAmplificationLevel(ItemStack stack) {
        if (!isResourceAmpEnabled(stack)) {
            return 0;
        }
        if (!stack.isEnchanted()) {
            return 0;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.RESOURCE_AMPLIFICATION.get(), stack);
    }


    public static int resolveResourceLevel(Attacker attacker, Entity directEntity) {
        if (attacker == null) {
            return 0;
        }
        if (directEntity != null && TaczIntegration.isTaczBulletEntity(directEntity)) {
            return TaczIntegration.getResourceAmplificationLevelFromBullet(directEntity);
        }
        return getEffectiveResourceAmplificationLevel(attacker.entity().getMainHandItem());
    }

    public static int resolveResourceLevel(Player player, Entity directEntity) {
        if (player == null) {
            return 0;
        }
        return resolveResourceLevel(new Attacker(player, classifyAttacker(player)), directEntity);
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
                        false);
                return false;
            }
            long now = System.currentTimeMillis();
            long lastSwitch = player.getPersistentData().getLong(RESOURCE_AMPLIFICATION_SWITCH_CD_TAG);
            if (now - lastSwitch < AmpConstants.RESOURCE_AMPLIFICATION_SWITCH_COOLDOWN_MS) {
                return false;
            }
            player.getPersistentData().putLong(RESOURCE_AMPLIFICATION_SWITCH_CD_TAG, now);
            boolean newState = !isResourceAmpEnabled(contextStack);
            setResourceAmpEnabled(contextStack, newState);
            player.displayClientMessage(buildResourceAmplificationToggleMessage(newState), false);
            return true;
        }
        return false;
    }
}
