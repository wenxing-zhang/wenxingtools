package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.entity.MultiStackItemEntity;
import com.wenxing.wenxingtools.integration.MalumSpiritAmplification;
import com.wenxing.wenxingtools.item.AutoSpawnEggs;
import com.wenxing.wenxingtools.util.DropWindowUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * 死亡掉落的「入世层」补放：第三方在死亡时绕过 {@code LivingDropsEvent} 直接发行的物品实体，
 * 从不进入 {@code captureDrops} 窗口，只能在它们进入世界时按死亡窗口识别并补倍率。
 * <p>
 * 只对「玩家击杀且增幅等级 &gt; 0」的死亡开窗；窗口内且落在死亡点半径内的物品实体才处理。
 * 标准链路的产物（{@link MultiStackItemEntity} 聚合体、Malum 待碎裂精魂）与玩家丢出的物品全部放行。
 */
@EventBusSubscriber(modid = WenXingTools.MODID)
public final class DropsJoinInterception {

    private static final boolean DEBUG = Boolean.getBoolean("wenxingtools.debug_drops");

    private static final class Window {
        final ServerLevel level;
        final double x;
        final double y;
        final double z;
        final long tick;
        final int resourceLevel;

        Window(ServerLevel level, double x, double y, double z, long tick, int resourceLevel) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tick = tick;
            this.resourceLevel = resourceLevel;
        }
    }

    private static final ThreadLocal<Window> WINDOW = new ThreadLocal<>();

    private DropsJoinInterception() {
    }

    /**
     * 开窗必须早于其他模组的死亡监听器：第三方常在自己的 {@code LivingDeathEvent} 监听器里当场
     * {@code spawnAtLocation}，那些物品在事件分发到我们之前就已入世，用 {@code LOWEST} 会整类漏掉。
     * {@code die()} 在本事件分发完毕后才进 {@code dropAllDeathLoot}，故开窗仍早于原版掉落阶段。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player player = ResourceAmplificationHandler.resolveKillAttribution(
                ResourceAmplificationHandler.resolvePlayerAttacker(event.getSource()), entity);
        if (player == null) {
            return;
        }
        int resourceLevel = ResourceAmplificationHandler.resolveResourceLevel(
                player, event.getSource().getDirectEntity());
        if (resourceLevel <= 0) {
            return;
        }
        WINDOW.set(new Window(serverLevel,
                entity.getX(), entity.getY(), entity.getZ(),
                serverLevel.getGameTime(), resourceLevel));
        if (DEBUG) {
            WenXingTools.LOGGER.info(
                    "[resource_amp] death window: type={} level={} at={} {} {}",
                    entity.getType(), resourceLevel, entity.getX(), entity.getY(), entity.getZ());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Window window = WINDOW.get();
        if (window == null) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel) || serverLevel != window.level) {
            return;
        }
        if (serverLevel.getGameTime() - window.tick > DropWindowUtil.WINDOW_TICKS) {
            WINDOW.remove();
            return;
        }
        // loadedFromDisk 表示随区块加载重新入世的既有实体，不是本次死亡的产物
        if (event.loadedFromDisk()) {
            return;
        }
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        // 本模组聚合体已在主流程放完倍率，再改 count 会二次放大
        if (itemEntity instanceof MultiStackItemEntity) {
            return;
        }
        if (MalumSpiritAmplification.isShatterPrimedDrop(itemEntity)) {
            return;
        }
        // 玩家丢弃 / 发射器射出的物品带 thrower，不属死亡产物
        if (itemEntity.getOwner() != null) {
            return;
        }
        double distanceSqr = itemEntity.distanceToSqr(window.x, window.y, window.z);
        if (!DropWindowUtil.inWindow(serverLevel.getGameTime(), window.tick, distanceSqr)) {
            return;
        }
        // 第三方物品实体的 getItem/setItem 可能被覆写，异常只跳过该实体，不得中断事件分发
        try {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) {
                return;
            }
            // 蛋类不放大，与主流程口径一致
            if (AutoSpawnEggs.isAmplificationExemptEgg(stack)) {
                return;
            }
            long multiplier = ResourceAmplificationHandler.computeKillDropMultiplier(window.resourceLevel, stack);
            long total = stack.getCount() * multiplier;
            if (total <= stack.getCount()) {
                return;
            }
            // 必须走 setItem 而不是原地 getItem().setCount()：1.21.1 的 ItemEntity 把物品放在
            // SynchedEntityData(DATA_ITEM) 里，原地改 count 只动服务端对象，不标脏、不同步，
            // 客户端会一直看到放大前的数量（拾取时才由服务端结算成新数量）。
            ItemStack amplified = stack.copy();
            amplified.setCount((int) Math.min(total, Integer.MAX_VALUE));
            itemEntity.setItem(amplified);
            if (DEBUG) {
                WenXingTools.LOGGER.info(
                        "[resource_amp] join amplified: item={} from={} to={} level={}",
                        amplified.getItem(), stack.getCount(), amplified.getCount(), window.resourceLevel);
            }
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[resource_amp] join amplify skipped: entity={} {}",
                    itemEntity.getClass().getName(), t.toString(), t);
        }
    }

    /** 世界卸载时清掉窗口，避免跨存档残留。 */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        WINDOW.remove();
    }
}
