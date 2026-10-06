package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = WenXingTools.MODID)
public final class ResourceAmplificationEvents {

    private static final ThreadLocal<Set<LivingEntity>> RESOURCE_DROP_CONTEXT = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<Set<LivingEntity>> RESOURCE_XP_CONTEXT = ThreadLocal.withInitial(HashSet::new);

    private static final int RESOURCE_CONTEXT_SHRINK_THRESHOLD = 64;

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
        int factor = resourceLevel > 0 ? AmpSupport.computeResourceFactor(resourceLevel) : 0;
        int spiritMultiplier = resourceLevel > 0
                ? com.wenxing.wenxingtools.integration.MalumSpiritIntegration.SPIRIT_MULTIPLIER
                : 1;

        // 死亡必合并：无增幅也按「物品+组件」收成单 MultiStack
        if (RESOURCE_DROP_CONTEXT.get().add(event.getEntity())) {
            try {
                applyResourceAmplificationToLivingDrops(event, factor, spiritMultiplier);
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
        int factor = AmpSupport.computeResourceFactor(resourceLevel);
        var drops = Block.getDrops(state, serverLevel, pos, be, player, useTool);
        com.wenxing.wenxingtools.util.DropStackUtil.popTotals(
                level, pos,
                com.wenxing.wenxingtools.util.DropStackUtil.mergeStacksMultiplied(drops, factor));
    }




    private static void applyResourceAmplificationToLivingDrops(LivingDropsEvent event, int factor, int spiritMultiplier) {
        LivingEntity target = event.getEntity();
        List<ItemStack> collected = new ArrayList<>();

        for (ItemEntity itemEntity : event.getDrops()) {
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                collected.add(stack.copy());
            }
        }

        if (factor > 0) {
            collectExtraLootTableStacks(target, event, collected);
        }

        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        Level level = target.level();

        java.util.Map<com.wenxing.wenxingtools.util.DropStackUtil.StackKey, Long> totals = new java.util.LinkedHashMap<>();
        long multiplier = 1L + factor;
        for (ItemStack stack : collected) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            long total = (long) stack.getCount() * multiplier;
            if (total > 0) {
                ItemStack keyStack = stack.copy();
                keyStack.setCount(1);
                totals.merge(new com.wenxing.wenxingtools.util.DropStackUtil.StackKey(keyStack), total, Long::sum);
            }
        }

        // 精魂（含 CachedSpiritDrops）并入同一 totals → 真正单实体
        com.wenxing.wenxingtools.integration.MalumSpiritIntegration.contributeNearbySpirits(
                target, spiritMultiplier, totals);

        event.getDrops().clear();
        if (!totals.isEmpty()) {
            event.getDrops().add(com.wenxing.wenxingtools.entity.MultiStackItemEntity.fromStacks(
                    level, x, y, z, com.wenxing.wenxingtools.util.DropStackUtil.totalsToStacks(totals)));
        }
    }


    private static void collectExtraLootTableStacks(LivingEntity target, LivingDropsEvent event, List<ItemStack> collected) {
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

        for (int roll = 0; roll < AmpConstants.EXTRA_LOOT_ROLLS; roll++) {

            for (ItemStack stack : lootTable.getRandomItems(params)) {
                if (!stack.isEmpty()) {
                    collected.add(stack.copy());
                }
            }
        }
    }
}
