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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 资源增幅（行为对齐 Forge 1.20.1）：击杀掉落 / 经验 / 方块破坏。
 * 公式：击杀掉落＝一次击杀捕获到的全部产物 ×4 份、每份 ×（可堆叠 level×25 / 不可堆叠 level×5）；经验 ×level×10000（直接入账）。
 */
@EventBusSubscriber(modid = WenXingTools.MODID)
public final class ResourceAmplificationHandler {
    private ResourceAmplificationHandler() {}

    public static final String RESOURCE_AMPLIFICATION_PLAYER_TAG = WenXingTools.MODID + ":resource_amp_enabled";
    private static final String RESOURCE_AMPLIFICATION_SWITCH_CD_TAG = WenXingTools.MODID + ":resource_amp_switch_cd";
    private static final int RESOURCE_AMPLIFICATION_SWITCH_COOLDOWN_MS = 250;
    private static final int RESOURCE_XP_CAP = 2000000000;
    private static final long RESOURCE_XP_MULTIPLIER = 100L;
    /** 击杀掉落复制份数：一次击杀的全部产物按「相当于击杀 4 个生物」计 */
    private static final int KILL_DROP_COPIES = 4;
    /** 击杀掉落每份的倍率系数：可堆叠物品每级 ×25（4 份合计 level×100） */
    private static final int KILL_FACTOR_STACKABLE_PER_COPY = 25;
    /** 击杀掉落每份的倍率系数：不可堆叠物品每级 ×5（4 份合计 level×20） */
    private static final int KILL_FACTOR_UNSTACKABLE_PER_COPY = 5;

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

        // 死亡必合并：无增幅也按「物品+组件」收成单 MultiStack
        int resourceLevel = player != null
                ? resolveResourceLevel(player, event.getSource().getDirectEntity())
                : 0;

        if (CombatReentry.enterResourceDrop(event.getEntity())) {
            try {
                processLivingDrops(event, resourceLevel);
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

    private static void processLivingDrops(LivingDropsEvent event, int resourceLevel) {
        LivingEntity dead = event.getEntity();
        // 捕获到的全部来源（战利品表 / 硬编码 / 装备 / 全局战利品修饰器 / 事件追加）整体复制 4 份，再按物品分层乘倍率
        Map<DropStackUtil.StackKey, Long> totals = collectMultipliedTotals(event, resourceLevel);
        DropStackUtil.replaceWithMerged(
                dead.level(), dead.getX(), dead.getY(), dead.getZ(),
                event.getDrops(), totals);
    }

    /**
     * 击杀掉落的整体倍率：全部产物复制 {@code KILL_DROP_COPIES} 份，每份按可堆叠分层（×25 / ×5）。
     * <p>无增幅（{@code level<=0}）返回 1，让原版掉落原样通过——击杀路径的「必合并」不依赖增幅开关。
     */
    private static long computeKillDropMultiplier(int level, ItemStack stack) {
        if (level <= 0 || stack == null || stack.isEmpty()) {
            return 1L;
        }
        long perCopy = (long) level * (stack.getMaxStackSize() > 1
                ? KILL_FACTOR_STACKABLE_PER_COPY
                : KILL_FACTOR_UNSTACKABLE_PER_COPY);
        return Math.min(perCopy * KILL_DROP_COPIES, (long) Integer.MAX_VALUE);
    }

    /** 原有掉落 → totals ×（4 份 × 每份分层倍率）；无增幅时 ×1；刷怪蛋不参与放大。 */
    private static Map<DropStackUtil.StackKey, Long> collectMultipliedTotals(LivingDropsEvent event, int resourceLevel) {
        Map<DropStackUtil.StackKey, Long> totals = new LinkedHashMap<>();
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
            totals.merge(new DropStackUtil.StackKey(keyStack),
                    (long) original.getCount() * computeKillDropMultiplier(resourceLevel, original), Long::sum);
        }
        return totals;
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
