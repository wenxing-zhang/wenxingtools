package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.integration.TaczIntegration;
import com.wenxing.wenxingtools.util.CombatReentry;
import com.wenxing.wenxingtools.util.CombatUtil;
import com.wenxing.wenxingtools.util.DropStackUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 资源增幅（行为对齐 Forge 1.20.1）：击杀掉落 / 经验 / 方块破坏。
 * 公式：掉落 ×(1+level×100)，额外 2 次战利品 roll，经验 ×level×10000（直接入账）。
 */
@EventBusSubscriber(modid = WenXingTools.MODID)
public final class ResourceAmplificationHandler {
    private ResourceAmplificationHandler() {}

    public static final String RESOURCE_AMPLIFICATION_PLAYER_TAG = WenXingTools.MODID + ":resource_amp_enabled";
    private static final String RESOURCE_AMPLIFICATION_SWITCH_CD_TAG = WenXingTools.MODID + ":resource_amp_switch_cd";
    private static final int RESOURCE_AMPLIFICATION_SWITCH_COOLDOWN_MS = 250;
    private static final int RESOURCE_XP_CAP = 2000000000;
    private static final long RESOURCE_XP_MULTIPLIER = 100L;
    /** 击杀时额外完整战利品表 roll 次数（对齐神化清道夫，次数固定为 2） */
    private static final int EXTRA_LOOT_ROLLS = 2;

    public static boolean isResourceAmplificationEnabled(Player player) {
        if (player == null) {
            return false;
        }
        var data = player.getPersistentData();
        if (!data.contains(RESOURCE_AMPLIFICATION_PLAYER_TAG)) {
            return false;
        }
        return data.getBoolean(RESOURCE_AMPLIFICATION_PLAYER_TAG);
    }

    public static void setResourceAmplificationEnabled(Player player, boolean enabled) {
        if (player == null) {
            return;
        }
        player.getPersistentData().putBoolean(RESOURCE_AMPLIFICATION_PLAYER_TAG, enabled);
    }

    public static int computeResourceFactor(int level) {
        if (level <= 0) {
            return 0;
        }
        long factor = (long) level * 100L;
        return (int) Math.min(factor, Integer.MAX_VALUE);
    }

    public static Component buildResourceAmplificationToggleMessage(boolean newState, int displayLevel) {
        ChatFormatting color = newState ? ChatFormatting.GREEN : ChatFormatting.RED;
        if (!newState) {
            return Component.translatable("msg.wenxingtools.resource_amp.off").withStyle(color);
        }
        if (displayLevel > 0) {
            int totalFactor = computeResourceFactor(displayLevel) + 1;
            return Component.translatable(
                    "msg.wenxingtools.resource_amp.on_stats",
                    totalFactor,
                    displayLevel * 10000
            ).withStyle(color);
        }
        return Component.translatable("msg.wenxingtools.resource_amp.on").withStyle(color);
    }

    private static int getEffectiveResourceAmplificationLevel(Player player, ItemStack stack) {
        if (!isResourceAmplificationEnabled(player) || stack == null || stack.isEmpty()) {
            return 0;
        }
        return EnchantUtil.getLevel(ModEnchantments.RESOURCE_AMPLIFICATION, stack, player);
    }

    public static int resolveMainHandResourceLevel(Player player) {
        return getEffectiveResourceAmplificationLevel(player, player.getMainHandItem());
    }

    private static int resolveResourceLevel(Player player, Entity directEntity) {
        if (directEntity != null) {
            int level = TaczIntegration.getResourceAmplificationLevelFromBullet(directEntity);
            if (level > 0 && isResourceAmplificationEnabled(player)) {
                return level;
            }
        }
        return getEffectiveResourceAmplificationLevel(player, player.getMainHandItem());
    }

    public static boolean tryToggleResourceAmplificationByPlayer(Player player, ItemStack contextStack) {
        if (player == null || player.level().isClientSide) {
            return false;
        }
        if (contextStack == null || contextStack.isEmpty()) {
            return false;
        }
        int level = EnchantUtil.getLevel(ModEnchantments.RESOURCE_AMPLIFICATION, contextStack, player);
        if (level <= 0) {
            player.displayClientMessage(
                    Component.translatable("msg.wenxingtools.resource_amp.no_enchant").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        long now = System.currentTimeMillis();
        long lastSwitch = player.getPersistentData().getLong(RESOURCE_AMPLIFICATION_SWITCH_CD_TAG);
        if (now - lastSwitch < RESOURCE_AMPLIFICATION_SWITCH_COOLDOWN_MS) {
            return false;
        }
        player.getPersistentData().putLong(RESOURCE_AMPLIFICATION_SWITCH_CD_TAG, now);
        boolean newState = !isResourceAmplificationEnabled(player);
        setResourceAmplificationEnabled(player, newState);
        player.displayClientMessage(buildResourceAmplificationToggleMessage(newState, level), true);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        Entity attacker = event.getSource().getEntity();
        Player player = (attacker instanceof Player p && !CombatUtil.isNotValidAttackerPlayer(p)) ? p : null;

        // 取消掉落时不得合并/吞精魂（drops 不会生成，discard 会丢物）
        if (event.isCanceled()) {
            if (player != null) {
                SpawnEggHarvest.dropSpawnEggHarvestLoot(event, player);
            }
            return;
        }

        // 死亡必合并：无增幅也按「物品+组件」收成单 MultiStack（倍率 ×1 / 精魂 ×1）
        int resourceLevel = player != null
                ? resolveResourceLevel(player, event.getSource().getDirectEntity())
                : 0;
        int factor = resourceLevel > 0 ? computeResourceFactor(resourceLevel) : 0;
        int spiritMultiplier = resourceLevel > 0
                ? com.wenxing.wenxingtools.integration.MalumSpiritAmplification.SPIRIT_MULTIPLIER
                : 1;

        if (CombatReentry.enterResourceDrop(event.getEntity())) {
            try {
                processLivingDrops(event, factor, spiritMultiplier);
            } catch (Throwable t) {
                WenXingTools.LOGGER.warn("[resource_amp] living drops merge failed: {}", t.toString(), t);
            } finally {
                CombatReentry.exitResourceDrop(event.getEntity());
            }
        }

        // 掠蛋在倍增/合并之后追加，数量不受资源增幅影响，顺序确定
        if (player != null) {
            SpawnEggHarvest.dropSpawnEggHarvestLoot(event, player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (event.isCanceled()) {
            return;
        }
        Player player = event.getAttackingPlayer();
        if (player == null || CombatUtil.isNotValidAttackerPlayer(player)) {
            return;
        }
        Entity directEntity = null;
        if (event.getEntity() != null && event.getEntity().getLastDamageSource() != null) {
            directEntity = event.getEntity().getLastDamageSource().getDirectEntity();
        }
        int resourceLevel = resolveResourceLevel(player, directEntity);
        if (resourceLevel <= 0) {
            return;
        }
        if (!CombatReentry.enterResourceXp(event.getEntity())) {
            return;
        }
        try {
            int factor = computeResourceFactor(resourceLevel);
            // 经验 ×level×10000（factor=level×100，再乘 100）；直接入账，不生成经验球
            long newXp = (long) event.getOriginalExperience() * (long) factor * RESOURCE_XP_MULTIPLIER;
            int granted = (int) Math.min(newXp, (long) RESOURCE_XP_CAP);
            event.setDroppedExperience(0);
            if (granted > 0) {
                player.giveExperiencePoints(granted);
            }
        } finally {
            CombatReentry.exitResourceXp(event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // 连锁采集/保护插件常取消 BreakEvent；已取消则方块不会被破坏，不补额外掉落
        if (event.isCanceled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || CombatUtil.isNotValidAttackerPlayer(player) || player.level().isClientSide) {
            return;
        }
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack mainHand = player.getMainHandItem();
        int resourceLevel = getEffectiveResourceAmplificationLevel(player, mainHand);
        if (resourceLevel <= 0) {
            return;
        }
        Level level = player.level();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        if (!state.canHarvestBlock(level, pos, player)) {
            return;
        }
        int factor = computeResourceFactor(resourceLevel);
        // 不取消 BreakEvent：原版会掉落 1 份，这里只补 factor 份额外
        // 兼容连锁采集：对方对每个方块各发一次 BreakEvent 时，每块都会走此分支放大
        processBlockExtraDrops(serverLevel, pos, state, player, factor);
    }

    // 成熟作物右键收割已按产品要求移除（两版一致）

    private static void processLivingDrops(LivingDropsEvent event, int factor, int spiritMultiplier) {
        LivingEntity dead = event.getEntity();
        // 先汇总 totals（物品+组件），再统一放大/追加 roll，最后一次拆分落地，避免中间实体峰值
        Map<DropStackUtil.StackKey, Long> totals = collectMultipliedTotals(event, factor);
        if (factor > 0) {
            appendExtraLootTotals(event, factor, totals);
        }
        // 精魂（含 CachedSpiritDrops）并入同一 totals → 真正单实体
        com.wenxing.wenxingtools.integration.MalumSpiritAmplification.contributeNearbySpirits(
                dead, spiritMultiplier, totals);
        DropStackUtil.replaceWithMerged(
                dead.level(), dead.getX(), dead.getY(), dead.getZ(),
                event.getDrops(), totals);
    }

    /** 原有掉落 → totals × (1+factor)；刷怪蛋不参与放大。 */
    private static Map<DropStackUtil.StackKey, Long> collectMultipliedTotals(LivingDropsEvent event, int factor) {
        Map<DropStackUtil.StackKey, Long> totals = new LinkedHashMap<>();
        long multiplier = 1L + factor;
        for (ItemEntity itemEntity : event.getDrops()) {
            ItemStack original = itemEntity.getItem();
            if (original.isEmpty()) {
                continue;
            }
            if (original.getItem() instanceof net.minecraft.world.item.SpawnEggItem) {
                ItemStack eggKey = original.copy();
                eggKey.setCount(1);
                totals.merge(new DropStackUtil.StackKey(eggKey), (long) original.getCount(), Long::sum);
                continue;
            }
            ItemStack keyStack = original.copy();
            keyStack.setCount(1);
            totals.merge(new DropStackUtil.StackKey(keyStack), (long) original.getCount() * multiplier, Long::sum);
        }
        return totals;
    }

    /**
     * 对死亡生物再完整求值 2 次战利品表（等同“再杀两次”的掉落生成），不触发 LivingDropsEvent。
     * 结果数量再乘 (1 + factor)，并入 totals。
     */
    private static void appendExtraLootTotals(
            LivingDropsEvent event,
            int factor,
            Map<DropStackUtil.StackKey, Long> totals) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (dead.isBaby()) {
            return;
        }
        if (!serverLevel.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            return;
        }
        ResourceKey<LootTable> lootTableKey = dead.getLootTable();
        if (lootTableKey == null) {
            return;
        }
        LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(lootTableKey);
        if (lootTable == null || lootTable == LootTable.EMPTY) {
            return;
        }

        Entity sourceEntity = event.getSource().getEntity();
        Player killer = sourceEntity instanceof Player player ? player : null;
        boolean recentlyHit = event.isRecentlyHit();
        long multiplier = 1L + factor;

        for (int roll = 0; roll < EXTRA_LOOT_ROLLS; roll++) {
            LootParams.Builder builder = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.THIS_ENTITY, dead)
                    .withParameter(LootContextParams.ORIGIN, dead.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                    .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, sourceEntity)
                    .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, event.getSource().getDirectEntity());
            if (recentlyHit && killer != null) {
                builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                        .withLuck(killer.getLuck());
            }
            LootParams params = builder.create(LootContextParamSets.ENTITY);
            // 不传 seed：每次 roll 使用 level 随机源，结果相互独立
            Collection<ItemStack> stacks = lootTable.getRandomItems(params);
            for (ItemStack stack : stacks) {
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack keyStack = stack.copy();
                keyStack.setCount(1);
                totals.merge(new DropStackUtil.StackKey(keyStack), (long) stack.getCount() * multiplier, Long::sum);
            }
        }
    }

    private static void processBlockExtraDrops(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos,
            BlockState state,
            Player player,
            int factor) {
        if (factor <= 0) {
            return;
        }
        var drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
        DropStackUtil.popTotals(level, pos, DropStackUtil.mergeStacksMultiplied(drops, factor));
    }

}
