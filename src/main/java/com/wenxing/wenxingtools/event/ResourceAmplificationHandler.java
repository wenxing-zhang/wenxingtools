package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.entity.MultiStackItemEntity;
import com.wenxing.wenxingtools.integration.MalumSpiritAmplification;
import com.wenxing.wenxingtools.integration.TaczIntegration;
import com.wenxing.wenxingtools.item.AutoSpawnEggs;
import com.wenxing.wenxingtools.item.ModItems;
import com.wenxing.wenxingtools.util.AmpRules;
import com.wenxing.wenxingtools.util.Attacker;
import com.wenxing.wenxingtools.util.CombatReentry;
import com.wenxing.wenxingtools.util.CombatUtil;
import com.wenxing.wenxingtools.util.DropStackUtil;
import com.wenxing.wenxingtools.util.SaturatingMath;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
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
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源增幅（行为对齐 Forge 1.20.1）：击杀掉落 / 经验 / 方块破坏。
 * 公式：击杀掉落＝一次击杀捕获到的全部产物 ×4 份、每份 ×（可堆叠 level×25 / 不可堆叠 level×5）；经验 ×level×10000（直接入账）。
 */
@EventBusSubscriber(modid = WenXingTools.MODID)
public final class ResourceAmplificationHandler {
    private ResourceAmplificationHandler() {}

    /** 旧载体（玩家 persistentData）：开关已迁到物品组件，重初始化时清理此键。 */
    public static final String LEGACY_RESOURCE_AMPLIFICATION_PLAYER_TAG = WenXingTools.MODID + ":resource_amp_enabled";
    private static final String RESOURCE_AMPLIFICATION_SWITCH_CD_TAG = WenXingTools.MODID + ":resource_amp_switch_cd";

    /**
     * 本次死亡的增幅等级，供「事件之后」的补扫读取。
     * <p>
     * 线程内传递：补扫与死亡掉落在同一线程（服务端主线程）同一次 {@code dropAllDeathLoot} 调用链里，
     * 没有可以携带该值的上下文对象；补扫被调用时即清理，未配对时不会被读取。
     */
    private static final ThreadLocal<Integer> LATE_SWEEP_LEVEL = new ThreadLocal<>();
    private static final int RESOURCE_AMPLIFICATION_SWITCH_COOLDOWN_MS = 250;
    private static final int RESOURCE_XP_CAP = 2000000000;
    private static final long RESOURCE_XP_MULTIPLIER = 100L;
    /** 击杀掉落复制份数：一次击杀的全部产物按「相当于击杀 4 个生物」计 */
    private static final int KILL_DROP_COPIES = 4;
    /** 击杀掉落每份的倍率系数：可堆叠物品每级 ×25（4 份合计 level×100） */
    private static final int KILL_FACTOR_STACKABLE_PER_COPY = 25;
    /** 击杀掉落每份的倍率系数：不可堆叠物品每级 ×5（4 份合计 level×20） */
    private static final int KILL_FACTOR_UNSTACKABLE_PER_COPY = 5;
    /** 方块破坏额外掉落的倍率系数：可堆叠物品每级 ×100（对齐 Forge 1.20.1） */
    private static final int RESOURCE_FACTOR_STACKABLE_PER_LEVEL = 100;
    /** 方块破坏额外掉落的倍率系数：不可堆叠物品每级 ×10（对齐 Forge 1.20.1） */
    private static final int RESOURCE_FACTOR_UNSTACKABLE_PER_LEVEL = 10;

    /** 开关读物品：跟着物品走，女仆与科技方块（假玩家）手持即可生效，不再依赖玩家身上的状态。 */
    public static boolean isResourceAmpEnabled(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.getOrDefault(ModItems.RESOURCE_AMP_ENABLED.get(), Boolean.FALSE);
    }

    /** 写开关组件；缺省即关闭，因此开启时写入、关闭时移除，不给物品留冗余组件。 */
    public static void setResourceAmpEnabled(ItemStack stack, boolean enabled) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (enabled) {
            stack.set(ModItems.RESOURCE_AMP_ENABLED.get(), Boolean.TRUE);
        } else {
            stack.remove(ModItems.RESOURCE_AMP_ENABLED.get());
        }
    }

    /** 旧存档清理：开关已不再读玩家 persistentData，登录/重生/切维时移除残留键。 */
    public static void clearLegacyPlayerSwitchTag(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        player.getPersistentData().remove(LEGACY_RESOURCE_AMPLIFICATION_PLAYER_TAG);
    }

    public static int computeResourceFactor(int level) {
        if (level <= 0) {
            return 0;
        }
        long factor = (long) level * 100L;
        return (int) Math.min(factor, Integer.MAX_VALUE);
    }

    /**
     * 方块破坏额外掉落的分层倍率（对齐 Forge 1.20.1）：可堆叠 {@code level×100}，不可堆叠（{@code maxStackSize == 1}）{@code level×10}。
     * <p>不可堆叠物品若吃满 ×100，单个 MultiStack 实体会塞进上百个单件条目、同步包体量失控，故单独降档。
     */
    private static long computeResourceFactorForStack(int level, ItemStack stack) {
        if (level <= 0 || stack == null || stack.isEmpty()) {
            return 0L;
        }
        return (long) level * (stack.getMaxStackSize() > 1
                ? RESOURCE_FACTOR_STACKABLE_PER_LEVEL
                : RESOURCE_FACTOR_UNSTACKABLE_PER_LEVEL);
    }

    /**
     * 开关提示只保留开 / 关两态。
     * <p>
     * 原先附带「资源倍率 / 经验倍率」：该数字按「额外倍率 +1」计算，与击杀通道实得（含原物的整份复制）
     * 差 1，与经验通道也不是同一量纲——两个数字都不是玩家拿到手的倍数，误导大于信息。
     */
    public static Component buildResourceAmplificationToggleMessage(boolean newState) {
        ChatFormatting color = newState ? ChatFormatting.GREEN : ChatFormatting.RED;
        return Component.translatable(
                newState ? "msg.wenxingtools.resource_amp.on" : "msg.wenxingtools.resource_amp.off")
                .withStyle(color);
    }

    private static int getEffectiveResourceAmplificationLevel(ItemStack stack, LivingEntity context) {
        if (stack == null || stack.isEmpty() || !isResourceAmpEnabled(stack)) {
            return 0;
        }
        return EnchantUtil.getLevel(ModEnchantments.RESOURCE_AMPLIFICATION, stack, context);
    }

    public static int resolveMainHandResourceLevel(Player player) {
        return player == null ? 0 : getEffectiveResourceAmplificationLevel(player.getMainHandItem(), player);
    }

    public static int resolveResourceLevel(Attacker attacker, Entity directEntity) {
        if (attacker == null) {
            return 0;
        }
        // 枪械：子弹上的等级已按开枪时的枪物品开关写入（TaczIntegration 侧拦），此处直接采信
        if (directEntity != null) {
            int bulletLevel = TaczIntegration.getResourceAmplificationLevelFromBullet(directEntity);
            if (bulletLevel > 0) {
                return bulletLevel;
            }
        }
        LivingEntity entity = attacker.entity();
        return getEffectiveResourceAmplificationLevel(entity.getMainHandItem(), entity);
    }

    public static int resolveResourceLevel(Player player, Entity directEntity) {
        if (player == null) {
            return 0;
        }
        return resolveResourceLevel(new Attacker(player, CombatUtil.classifyAttacker(player)), directEntity);
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
                    false);
            return false;
        }
        long now = System.currentTimeMillis();
        long lastSwitch = player.getPersistentData().getLong(RESOURCE_AMPLIFICATION_SWITCH_CD_TAG);
        if (now - lastSwitch < RESOURCE_AMPLIFICATION_SWITCH_COOLDOWN_MS) {
            return false;
        }
        player.getPersistentData().putLong(RESOURCE_AMPLIFICATION_SWITCH_CD_TAG, now);
        boolean newState = !isResourceAmpEnabled(contextStack);
        setResourceAmpEnabled(contextStack, newState);
        player.displayClientMessage(buildResourceAmplificationToggleMessage(newState), false);
        syncStackToClient(player);
        return true;
    }

    /** 组件改动不会自动下发：广播一次容器变化，让客户端手持粒子与服务端开关一致。 */
    private static void syncStackToClient(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        // 供事件之后的补扫读取：本次死亡的增幅等级。每次死亡都重置，未经过本监听器则保持 null
        LATE_SWEEP_LEVEL.set(0);
        Attacker attacker = CombatUtil.resolveKillAttacker(event.getSource(), event.getEntity());

        // 取消掉落时不得合并/吞精魂（drops 不会生成，discard 会丢物）
        if (event.isCanceled()) {
            if (attacker != null) {
                SpawnEggHarvest.dropSpawnEggHarvestLoot(event, attacker);
            }
            return;
        }

        // 死亡必合并：无增幅也按「物品+组件」收成单 MultiStack
        int resourceLevel = attacker != null
                ? resolveResourceLevel(attacker, event.getSource().getDirectEntity())
                : 0;
        LATE_SWEEP_LEVEL.set(resourceLevel);

        if (CombatReentry.enterResourceDrop(event.getEntity())) {
            try {
                processLivingDrops(event, resourceLevel);
            } catch (OutOfMemoryError e) {
                // 堆已耗尽时继续兜底只会把同一路径再跑一遍，交给外层终止
                throw e;
            } catch (Throwable t) {
                WenXingTools.LOGGER.warn("[resource_amp] living drops merge failed: {}", t.toString(), t);
            } finally {
                CombatReentry.exitResourceDrop(event.getEntity());
            }
        }

        // 掠蛋在倍增/合并之后追加，数量不受资源增幅影响，顺序确定
        if (attacker != null) {
            SpawnEggHarvest.dropSpawnEggHarvestLoot(event, attacker);
        }
    }

    /**
     * 事件之后追加掉落的补扫。{@code LivingDropsEvent} 里同为 {@code LOWEST} 但注册更晚的监听器，
     * 其追加的物品在主流程遍历时还不存在；而原版在事件返回后才会把集合逐个入世，
     * 因此由 Mixin 挂在 {@code CommonHooks.onLivingDrops} 返回之后补一次同样口径的放大。
     * <p>
     * 注入点必须是事件<b>之后</b>：{@code captureDrops(null)} 早于事件，挂在那里读到的是上一次死亡留下的
     * 上下文，且集合中尚无追加物。
     * <p>
     * 放行清单与主流程一致：只排本模组聚合体与 Malum 待碎裂精魂，其余物品实体（含第三方自定义子类）
     * 一并吃增幅；刷怪蛋按豁免口径原样并入。
     */
    public static void sweepLateDrops(LivingEntity entity, Collection<ItemEntity> drops, boolean canceled) {
        Integer levelBoxed = LATE_SWEEP_LEVEL.get();
        LATE_SWEEP_LEVEL.remove();
        if (canceled || levelBoxed == null || drops == null || drops.isEmpty()) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        int resourceLevel = levelBoxed;
        try {
            List<ItemEntity> late = new ArrayList<>();
            for (ItemEntity itemEntity : drops) {
                // 只排本模组聚合体（拒二次入料）与 Malum 待碎裂精魂；其余物品实体一并吃增幅
                if (itemEntity == null || itemEntity instanceof MultiStackItemEntity) {
                    continue;
                }
                if (MalumSpiritAmplification.isShatterPrimedDrop(itemEntity)) {
                    continue;
                }
                late.add(itemEntity);
            }
            if (late.isEmpty()) {
                return;
            }
            Map<DropStackUtil.StackKey, Long> totals = new LinkedHashMap<>();
            for (ItemEntity itemEntity : late) {
                ItemStack stack = itemEntity.getItem();
                if (stack.isEmpty()) {
                    continue;
                }
                if (AutoSpawnEggs.isAmplificationExemptEgg(stack)) {
                    ItemStack eggKey = stack.copy();
                    eggKey.setCount(1);
                    totals.merge(new DropStackUtil.StackKey(eggKey), (long) stack.getCount(), Long::sum);
                    continue;
                }
                long multiplier = computeKillDropMultiplier(resourceLevel, stack);
                long total = SaturatingMath.multiply(stack.getCount(), multiplier);
                if (total > 0) {
                    ItemStack keyStack = stack.copy();
                    keyStack.setCount(1);
                    totals.merge(new DropStackUtil.StackKey(keyStack), total, Long::sum);
                }
            }
            if (totals.isEmpty()) {
                return;
            }
            List<ItemStack> lateStacks = DropStackUtil.totalsToStacks(totals);
            if (lateStacks.isEmpty()) {
                return;
            }
            // 优先并入主流程已生成的多合一实体，避免一次死亡落地两个聚合体
            MultiStackItemEntity existing = null;
            for (ItemEntity candidate : drops) {
                if (candidate instanceof MultiStackItemEntity multiStack) {
                    existing = multiStack;
                    break;
                }
            }
            if (existing != null) {
                for (ItemStack stack : lateStacks) {
                    existing.mergeStack(stack);
                }
                drops.removeAll(late);
            } else {
                // 先构造再替换：构造失败时原物品仍在集合里，不会被整段吞掉
                MultiStackItemEntity merged = MultiStackItemEntity.fromStacks(
                        serverLevel, entity.getX(), entity.getY(), entity.getZ(), lateStacks);
                if (merged == null) {
                    return;
                }
                drops.removeAll(late);
                drops.add(merged);
            }
            if (WenXingTools.LOGGER.isDebugEnabled()) {
                WenXingTools.LOGGER.debug(
                        "[resource_amp] late drops swept: type={}, items={}, level={}, entries={}, mergedIntoExisting={}",
                        entity.getType(), late.size(), resourceLevel, totals.size(), existing != null);
            }
        } catch (OutOfMemoryError e) {
            // 堆已耗尽时继续兜底只会把同一路径再跑一遍，交给外层终止
            throw e;
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[resource_amp] late drops sweep failed: {}", t.toString(), t);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target == null) {
            return;
        }
        DamageSource lastSource = target.getLastDamageSource();
        Entity directEntity = lastSource != null ? lastSource.getDirectEntity() : null;
        Attacker attacker = CombatUtil.resolveKillAttacker(lastSource, target);
        if (attacker == null || !AmpRules.shouldAmplifyXp(attacker.kind())) {
            return;
        }
        int resourceLevel = resolveResourceLevel(attacker, directEntity);
        if (resourceLevel <= 0) {
            return;
        }
        if (!CombatReentry.enterResourceXp(event.getEntity())) {
            return;
        }
        try {
            int factor = computeResourceFactor(resourceLevel);
            // 经验 ×level×10000（factor=level×100，再乘 100）；直接入账，不生成经验球
            // 附魔等级可来自 NBT，裸乘会溢出成负值让经验静默归零，故走饱和乘
            long newXp = SaturatingMath.multiply(
                    SaturatingMath.multiply(event.getOriginalExperience(), factor),
                    RESOURCE_XP_MULTIPLIER);
            int granted = (int) Math.min(newXp, (long) RESOURCE_XP_CAP);
            event.setDroppedExperience(0);
            if (granted > 0) {
                Player realPlayer = attacker.asPlayer();
                if (realPlayer != null) {
                    realPlayer.giveExperiencePoints(granted);
                }
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
        if (player == null || player.level().isClientSide) {
            return;
        }
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack mainHand = player.getMainHandItem();
        int resourceLevel = getEffectiveResourceAmplificationLevel(mainHand, player);
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
        // 不取消 BreakEvent：原版会掉落 1 份，这里只补分层倍率的额外份
        // 兼容连锁采集：对方对每个方块各发一次 BreakEvent 时，每块都会走此分支放大
        processBlockExtraDrops(serverLevel, pos, state, player, resourceLevel);
    }

    /**
     * 挖掘方块的经验增幅（对齐 Forge 1.20.1）。
     * <p>1.21 起原版把方块经验从 {@code Block#playerDestroy} 挪到掉落事件，入口由 1.20.1 的
     * {@code BlockEvent.BreakEvent#getExpToDrop} 变为本事件的 {@code getDroppedExperience}——语义相同、类不同。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDropsExperience(BlockDropsEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getBreaker() instanceof Player player)) {
            return;
        }
        if (!AmpRules.shouldAmplifyXp(CombatUtil.classifyAttacker(player))) {
            return;
        }
        if (player.getAbilities().instabuild) {
            return;
        }
        int exp = event.getDroppedExperience();
        if (exp <= 0) {
            return;
        }
        int resourceLevel = getEffectiveResourceAmplificationLevel(player.getMainHandItem(), player);
        if (resourceLevel <= 0) {
            return;
        }
        long amplified = SaturatingMath.multiply(exp, 1L + (long) computeResourceFactor(resourceLevel));
        event.setDroppedExperience((int) Math.min(amplified, (long) RESOURCE_XP_CAP));
    }

    // 成熟作物右键收割已按产品要求移除（两版一致）

    private static void processLivingDrops(LivingDropsEvent event, int resourceLevel) {
        LivingEntity dead = event.getEntity();
        // Malum 打过「待碎裂」标记的战利品必须原样入世，否则精魂生成链断裂
        List<ItemEntity> passthrough = new ArrayList<>();
        for (ItemEntity itemEntity : event.getDrops()) {
            if (MalumSpiritAmplification.isShatterPrimedDrop(itemEntity)) {
                passthrough.add(itemEntity);
            }
        }
        event.getDrops().removeAll(passthrough);
        // 捕获到的全部来源（战利品表 / 硬编码 / 装备 / 全局战利品修饰器 / 事件追加）整体复制 4 份，再按物品分层乘倍率
        Map<DropStackUtil.StackKey, Long> totals = collectMultipliedTotals(event, resourceLevel);
        DropStackUtil.replaceWithMerged(
                dead.level(), dead.getX(), dead.getY(), dead.getZ(),
                event.getDrops(), totals);
        event.getDrops().addAll(passthrough);
    }

    /**
     * 击杀掉落的整体倍率：全部产物复制 {@code KILL_DROP_COPIES} 份，每份按可堆叠分层（×25 / ×5）。
     * <p>无增幅（{@code level<=0}）返回 1，让原版掉落原样通过——击杀路径的「必合并」不依赖增幅开关。
     */
    public static long computeKillDropMultiplier(int level, ItemStack stack) {
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
            if (AutoSpawnEggs.isAmplificationExemptEgg(original)) {
                ItemStack eggKey = original.copy();
                eggKey.setCount(1);
                totals.merge(new DropStackUtil.StackKey(eggKey), (long) original.getCount(), Long::sum);
                continue;
            }
            ItemStack keyStack = original.copy();
            keyStack.setCount(1);
            totals.merge(new DropStackUtil.StackKey(keyStack),
                    SaturatingMath.multiply(original.getCount(), computeKillDropMultiplier(resourceLevel, original)),
                    Long::sum);
        }
        return totals;
    }

    private static void processBlockExtraDrops(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos,
            BlockState state,
            Player player,
            int resourceLevel) {
        if (resourceLevel <= 0) {
            return;
        }
        var drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
        DropStackUtil.popTotals(level, pos,
                DropStackUtil.mergeStacksMultipliedBy(drops, stack -> computeResourceFactorForStack(resourceLevel, stack)));
    }

}
