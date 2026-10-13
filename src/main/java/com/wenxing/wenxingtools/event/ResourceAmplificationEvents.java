package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.mixin.LootPoolAccessor;
import com.wenxing.wenxingtools.mixin.LootTableAccessor;
import com.wenxing.wenxingtools.util.CombatUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = WenXingTools.MODID)
public final class ResourceAmplificationEvents {

    private static final ThreadLocal<Set<LivingEntity>> RESOURCE_DROP_CONTEXT = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<Set<LivingEntity>> RESOURCE_XP_CONTEXT = ThreadLocal.withInitial(HashSet::new);

    private static final int RESOURCE_CONTEXT_SHRINK_THRESHOLD = 64;

    /** 「全掉落」单次击杀最多追加的 entry 数：Boss / 箱子表条目多，防单实体 NBT 与同步包爆量 */
    private static final int ALL_ENTRY_DROP_LIMIT = 64;

    private ResourceAmplificationEvents() {
    }

    private static void shrinkResourceContextIfNeeded(ThreadLocal<Set<LivingEntity>> holder) {
        Set<LivingEntity> set = holder.get();
        if (set != null && set.size() >= RESOURCE_CONTEXT_SHRINK_THRESHOLD) {
            holder.remove();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        Player player = AmpSupport.resolvePlayerAttacker(event.getSource());
        if (player != null && CombatUtil.isNotValidAttackerPlayer(player)) {
            player = null;
        }

        // 取消掉落时不得合并/吞精魂
        if (event.isCanceled()) {
            if (player != null) {
                SpawnEggHarvest.dropSpawnEggHarvestLoot(event, player);
            }
            return;
        }

        int resourceLevel = player != null
                ? AmpSupport.resolveResourceLevel(player, event.getSource().getDirectEntity())
                : 0;

        // 死亡必合并：无增幅也按「物品+组件」收成单 MultiStack
        if (RESOURCE_DROP_CONTEXT.get().add(event.getEntity())) {
            try {
                applyResourceAmplificationToLivingDrops(event, resourceLevel);
            } catch (Throwable t) {
                WenXingTools.LOGGER.warn("[resource_amp] living drops merge failed: {}", t.toString(), t);
            } finally {
                RESOURCE_DROP_CONTEXT.get().remove(event.getEntity());
                shrinkResourceContextIfNeeded(RESOURCE_DROP_CONTEXT);
            }
        }

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
        int resourceLevel = AmpSupport.resolveResourceLevel(player, directEntity);
        if (resourceLevel <= 0) {
            return;
        }

        if (!RESOURCE_XP_CONTEXT.get().add(event.getEntity())) {
            return;
        }
        try {
            int factor = AmpSupport.computeResourceFactor(resourceLevel);

            long newXp = (long) event.getOriginalExperience() * (long) factor * AmpConstants.RESOURCE_XP_MULTIPLIER;
            int granted = (int) Math.min(newXp, (long) AmpConstants.RESOURCE_XP_CAP);

            event.setDroppedExperience(0);
            if (granted > 0) {
                grantExperienceSafely(player, granted);
            }
        } finally {
            RESOURCE_XP_CONTEXT.get().remove(event.getEntity());
            shrinkResourceContextIfNeeded(RESOURCE_XP_CONTEXT);
        }
    }

    /**
     * 挖掘方块的经验增幅。
     * <p>
     * 挂在这里而不是 {@code Block.dropResources}：Forge 让 {@code Block.playerDestroy} 传 {@code dropXp=false}
     * （见 Block.java.patch 注释「Don't drop xp as part of the resources」），玩家破坏路径的经验改由
     * {@code ServerPlayerGameMode#destroyBlock} 经 {@code ForgeHooks.onBlockBreakEvent} 返回值发放——
     * 即本事件的 {@link BlockEvent.BreakEvent#getExpToDrop()}。改写它即可让倍率落在唯一发放点上。
     */
    @SubscribeEvent
    public static void onBlockBreakExperience(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || CombatUtil.isNotValidAttackerPlayer(player)) {
            return;
        }
        if (player.getAbilities().instabuild) {
            return;
        }
        int exp = event.getExpToDrop();
        if (exp <= 0) {
            return;
        }
        int resourceLevel = AmpSupport.getEffectiveResourceAmplificationLevel(player, player.getMainHandItem());
        if (resourceLevel <= 0) {
            return;
        }
        long amplified = (long) exp * (1L + (long) AmpSupport.computeResourceFactor(resourceLevel));
        event.setExpToDrop((int) Math.min(amplified, (long) AmpConstants.RESOURCE_XP_CAP));
    }

    public static void clearPlayerResourceContexts() {
        RESOURCE_DROP_CONTEXT.remove();
        RESOURCE_XP_CONTEXT.remove();
    }

    private static void grantExperienceSafely(Player player, int granted) {
        int xpNeeded = Math.max(1, player.getXpNeededForNextLevel());
        int levels = granted / xpNeeded;
        int remainder = granted % xpNeeded;
        if (levels > 0) {
            player.giveExperienceLevels(levels);
        }
        if (remainder > 0) {
            player.giveExperiencePoints(remainder);
        }
    }


    public static void amplifyBlockDrops(Level level, BlockPos pos, BlockState state, Player player,
                                         net.minecraft.world.level.block.entity.BlockEntity be, ItemStack tool) {
        if (level == null || level.isClientSide || pos == null || state == null) {
            return;
        }
        if (player == null || CombatUtil.isNotValidAttackerPlayer(player)) {
            return;
        }
        if (player.getAbilities().instabuild) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack useTool = (tool != null && !tool.isEmpty()) ? tool : player.getMainHandItem();
        int resourceLevel = AmpSupport.getEffectiveResourceAmplificationLevel(player, useTool);
        if (resourceLevel <= 0) {
            return;
        }
        if (!state.canHarvestBlock(level, pos, player)) {
            return;
        }
        var drops = Block.getDrops(state, serverLevel, pos, be, player, useTool);
        com.wenxing.wenxingtools.util.DropStackUtil.popTotals(
                level, pos,
                com.wenxing.wenxingtools.util.DropStackUtil.mergeStacksMultipliedBy(
                        drops, dropped -> AmpSupport.computeResourceFactorForStack(resourceLevel, dropped)));
    }




    private static void applyResourceAmplificationToLivingDrops(LivingDropsEvent event, int resourceLevel) {
        LivingEntity target = event.getEntity();
        List<ItemStack> collected = new ArrayList<>();
        // Malum 打过「待碎裂」标记的战利品必须原样入世，否则精魂生成链断裂
        List<ItemEntity> passthrough = new ArrayList<>();

        for (ItemEntity itemEntity : event.getDrops()) {
            if (com.wenxing.wenxingtools.integration.MalumSpiritIntegration.isShatterPrimedDrop(itemEntity)) {
                passthrough.add(itemEntity);
                continue;
            }
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                collected.add(stack.copy());
            }
        }

        // 有增幅时不再随机 roll 额外次数，而是把战利品表内每个 entry 各追加一份（尊重 entry 自身条件）
        if (resourceLevel > 0) {
            collectAllLootTableStacks(target, event, collected);
        }

        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        Level level = target.level();

        java.util.Map<com.wenxing.wenxingtools.util.DropStackUtil.StackKey, Long> totals = new java.util.LinkedHashMap<>();
        for (ItemStack stack : collected) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            long multiplier = 1L + (long) AmpSupport.computeResourceFactorForStack(resourceLevel, stack);
            long total = (long) stack.getCount() * multiplier;
            if (total > 0) {
                ItemStack keyStack = stack.copy();
                keyStack.setCount(1);
                totals.merge(new com.wenxing.wenxingtools.util.DropStackUtil.StackKey(keyStack), total, Long::sum);
            }
        }

        // 先构造合并实体再替换：构造抛异常时原掉落仍在列表里，不会被整段吞掉
        com.wenxing.wenxingtools.entity.MultiStackItemEntity merged = null;
        if (!totals.isEmpty()) {
            merged = com.wenxing.wenxingtools.entity.MultiStackItemEntity.fromStacks(
                    level, x, y, z, com.wenxing.wenxingtools.util.DropStackUtil.totalsToStacks(totals));
        }
        event.getDrops().clear();
        event.getDrops().addAll(passthrough);
        if (merged != null) {
            event.getDrops().add(merged);
        }
    }


    /**
     * 把战利品表内每个 entry 各追加一份（原版那份随机掉落已由 {@code LivingDropsEvent} 给出，两者按物品+NBT 累加）。
     * <p>
     * entry 自身的条件由 {@code LootPoolEntryContainer#expand} 内部的 {@code canRun} 尊重，条件不满足者不产出；
     * pool 级条件单独判。条目数受 {@link #ALL_ENTRY_DROP_LIMIT} 闸门保护，超限截断并留日志。
     */
    private static void collectAllLootTableStacks(LivingEntity target, LivingDropsEvent event, List<ItemStack> collected) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return;
        }

        ResourceLocation lootTableId = target.getLootTable();
        if (lootTableId == null) {
            return;
        }
        LootTable lootTable = server.getLootData().getLootTable(lootTableId);
        if (lootTable == LootTable.EMPTY) {
            return;
        }

        DamageSource source = event.getSource();

        boolean recentlyHit = event.isRecentlyHit();
        LootParams.Builder builder = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.THIS_ENTITY, target)
                .withParameter(LootContextParams.ORIGIN, target.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, source.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, source.getDirectEntity());
        Player lastHurtByPlayer = target.getKillCredit() instanceof Player creditPlayer ? creditPlayer : null;
        if (recentlyHit && lastHurtByPlayer != null) {
            builder = builder
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, lastHurtByPlayer)
                    .withLuck(lastHurtByPlayer.getLuck());
        }
        LootParams params = builder.create(LootContextParamSets.ENTITY);
        LootContext context = new LootContext.Builder(params)
                .withOptionalRandomSeed(serverLevel.random.nextLong())
                .create(lootTableId);

        List<LootPool> pools = ((LootTableAccessor) (Object) lootTable).wenxingtools$getPools();
        if (pools == null || pools.isEmpty()) {
            return;
        }

        int appended = 0;
        for (LootPool pool : pools) {
            if (pool == null || appended >= ALL_ENTRY_DROP_LIMIT) {
                break;
            }
            LootPoolAccessor poolAccessor = (LootPoolAccessor) (Object) pool;
            var poolCondition = poolAccessor.wenxingtools$getCompositeCondition();
            if (poolCondition != null && !poolCondition.test(context)) {
                continue;
            }
            LootPoolEntryContainer[] containers = poolAccessor.wenxingtools$getEntries();
            if (containers == null) {
                continue;
            }
            for (LootPoolEntryContainer container : containers) {
                if (container == null) {
                    continue;
                }
                if (appended >= ALL_ENTRY_DROP_LIMIT) {
                    break;
                }
                container.expand(context, entry -> entry.createItemStack(dropped -> {
                    if (dropped != null && !dropped.isEmpty()) {
                        collected.add(dropped.copy());
                    }
                }, context));
                appended++;
            }
        }
        if (appended >= ALL_ENTRY_DROP_LIMIT) {
            WenXingTools.LOGGER.warn("[resource_amp] loot table {} truncated at {} entries (all-drop guard)",
                    lootTableId, ALL_ENTRY_DROP_LIMIT);
        }
    }
}
