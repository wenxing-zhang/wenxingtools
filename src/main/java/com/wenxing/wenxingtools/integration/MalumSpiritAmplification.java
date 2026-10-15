package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.entity.MultiStackItemEntity;
import com.wenxing.wenxingtools.event.ResourceAmplificationHandler;
import com.wenxing.wenxingtools.util.CombatUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Malum 精魂识别 / 增幅工具。
 * <p>
 * 精魂是 Malum 的 {@code SpiritItemEntity}（{@code FloatingItemEntity} 子类）：自动飞向击杀者并被吸收，
 * 不是普通掉落物。「先让它入世、再移除」会踩到硬坑——实体在生成包发出前就被移除，服务端仍为「已移除的实体」
 * 发追踪包，客户端据此造出收不到销毁包的幽灵实体，在生成点越堆越多。
 * <p>
 * 因此改为在 {@link EntityJoinLevelEvent} 上<b>直接取消精魂入世</b>，把放大后的物品立刻交付：
 * <ul>
 *   <li>有击杀者归属 + 开增幅：×{@value #SPIRIT_MULTIPLIER} 后交付；</li>
 *   <li>无击杀者归属：转交最近玩家（按其增幅状态放大）；附近确无玩家则直接取消（原本也只能原地自毁）；</li>
 *   <li>有归属但未开增幅：不拦截，交回 Malum 原生飞行收集；</li>
 *   <li>随区块重载重新入世：不拦截——取消会让它在每次区块加载时被重复投递。</li>
 * </ul>
 * 交付优先走 Malum 的 {@code pickupSpirit}（保留精魂袋 / 收集事件 / 溢出落地语义），不可用时自主入包或落地。
 * 反射仅用于 Malum 侧类型（第三方模组不参与本仓库的映射改名，源码名即运行时名）；失败一律保守降级（不丢精魂）。
 * <p>
 * 版本适配：入账入口在 Malum 1.21.1 为 {@code com.sammy.malum.core.handlers.SoulHarvestHandler}
 * （1.20.1 侧为 {@code SpiritHarvestHandler}）——本仓库对齐 NeoForge 1.21.1，故取前者。
 */
public final class MalumSpiritAmplification {

    /** 精魂资源增幅固定倍率（与掉落物的 (1+level×100) 不同） */
    public static final int SPIRIT_MULTIPLIER = 10;

    /** 无归属精魂兜底转交的搜索半径（格）；超出则视为无人接收，直接取消入世 */
    private static final double SALVAGE_RANGE = 64.0D;

    private static final String SPIRIT_ITEM_ENTITY = "com.sammy.malum.common.entity.spirit.SpiritItemEntity";
    private static final String SPIRIT_ITEM_ENTITY_ALT = "com.sammy.malum.common.entity.SpiritItemEntity";
    private static final String FLOATING_ITEM_ENTITY = "com.sammy.malum.common.entity.FloatingItemEntity";
    private static final String SOUL_HARVEST_HANDLER = "com.sammy.malum.core.handlers.SoulHarvestHandler";

    private static Method getItemMethod;
    private static Method getDestinationMethod;
    private static Method getEntityCollectorMethod;

    private static boolean pickupSpiritResolved;
    private static Method pickupSpiritMethod;

    private MalumSpiritAmplification() {
    }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                MalumSpiritAmplification::onEntityJoin);
        WenXingTools.LOGGER.debug("[resource_amp] Malum spirit helpers registered (amp x{})", SPIRIT_MULTIPLIER);
    }

    /** 精魂入世：取消其入世并把放大后的物品直接交付——实体不进入世界，从根本上消灭幽灵实体与堆积。 */
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity == null || !isSpiritDropEntity(entity)) {
            return;
        }
        // 随区块重载重新入世的精魂不拦截：取消会让它在每次区块加载时被重复投递
        if (event.loadedFromDisk()) {
            return;
        }

        Player owner = resolveSpiritOwner(serverLevel, entity);
        boolean owned = owner != null;
        if (!owned) {
            owner = nearestPlayer(serverLevel, entity);
        }
        if (owner == null) {
            // 无人可接收：直接取消入世（原本也只能原地悬浮到寿命上限自毁）
            event.setCanceled(true);
            return;
        }
        if (owned && resolveSpiritMultiplier(owner) <= 1) {
            // 有击杀者归属且无资源增幅：不拦截，交回 Malum 原生飞行收集
            return;
        }

        ItemStack current = getEntityItem(entity);
        if (current == null || current.isEmpty()) {
            event.setCanceled(true);
            return;
        }
        long amplified = (long) current.getCount() * Math.max(1, resolveSpiritMultiplier(owner));
        int count = (int) Math.min(amplified, Integer.MAX_VALUE);
        if (count <= 0) {
            event.setCanceled(true);
            return;
        }
        ItemStack delivered = current.copy();
        delivered.setCount(count);
        Player target = owner;

        // 取消入世：实体不会进入世界，因此没有生成包 / 追踪 / 幽灵实体，也不会堆积
        event.setCanceled(true);
        // 世界交互（入包 / 落地）延到下一刻执行，避免在区块尚未就绪时触发交互
        serverLevel.getServer().execute(() -> deliverToOwner(target, delivered));
    }

    /** 最近的在线玩家（同维度、非旁观、{@value #SALVAGE_RANGE} 格内）；没有则返回 null。 */
    private static Player nearestPlayer(ServerLevel level, Entity entity) {
        Player best = null;
        double bestDist = SALVAGE_RANGE * SALVAGE_RANGE;
        for (ServerPlayer player : level.players()) {
            if (CombatUtil.isNotValidAttackerPlayer(player)) {
                continue;
            }
            double dist = player.distanceToSqr(entity);
            if (dist <= bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    /**
     * 交付给玩家：优先走 Malum 的 pickupSpirit（保留精魂袋 / 收集事件 / 溢出落地语义）；
     * 该入口不可用或抛错时自主交付——先入背包，仍放不下则落地成普通掉落物（可拾取、可被磁铁吸取）。
     */
    private static void deliverToOwner(Player owner, ItemStack delivered) {
        if (owner.isRemoved()) {
            return;
        }
        Method method = resolvePickupSpiritMethod();
        if (method != null) {
            try {
                method.invoke(null, owner, delivered);
                return;
            } catch (Throwable t) {
                WenXingTools.LOGGER.warn("[resource_amp] Malum spirit pickup failed, delivering directly: {}", t.toString());
            }
        }
        if (!delivered.isEmpty()) {
            owner.getInventory().add(delivered);
            if (!delivered.isEmpty()) {
                owner.drop(delivered, false);
            }
        }
    }

    /** Malum 精魂入账入口；缺失 / 改名时返回 null 并降级为自主交付（只提示一次）。 */
    private static Method resolvePickupSpiritMethod() {
        if (!pickupSpiritResolved) {
            pickupSpiritResolved = true;
            try {
                Class<?> handler = Class.forName(SOUL_HARVEST_HANDLER);
                pickupSpiritMethod = handler.getMethod("pickupSpirit", LivingEntity.class, ItemStack.class);
            } catch (Throwable t) {
                WenXingTools.LOGGER.warn("[resource_amp] Malum spirit pickup entry unavailable; using direct delivery: {}", t.toString());
            }
        }
        return pickupSpiritMethod;
    }

    /** 击杀者：精魂的目标收集者 → 在线玩家。任一环节失败返回 null（交由就近兜底）。 */
    private static Player resolveSpiritOwner(ServerLevel level, Entity spirit) {
        Player player = resolveCollectorPlayer(level, spirit);
        if (player == null || CombatUtil.isNotValidAttackerPlayer(player)) {
            return null;
        }
        return player;
    }

    /** 击杀者是否处于资源增幅：现算等级，>0 返回 ×{@value #SPIRIT_MULTIPLIER}，否则 ×1。 */
    private static int resolveSpiritMultiplier(Player player) {
        return ResourceAmplificationHandler.resolveMainHandResourceLevel(player) > 0 ? SPIRIT_MULTIPLIER : 1;
    }

    /**
     * 取精魂的目标收集者：{@code FloatingEntity.getDestination()} → {@code FloatingItemDestinationData.getEntityCollector(level)}。
     * 两个方法在 Malum 侧均为 public，反射仅用于避免硬依赖。
     */
    private static Player resolveCollectorPlayer(ServerLevel level, Entity spirit) {
        try {
            if (getDestinationMethod == null || !getDestinationMethod.getDeclaringClass().isInstance(spirit)) {
                getDestinationMethod = spirit.getClass().getMethod("getDestination");
            }
            Object destination = getDestinationMethod.invoke(spirit);
            if (destination == null) {
                return null;
            }
            if (getEntityCollectorMethod == null || !getEntityCollectorMethod.getDeclaringClass().isInstance(destination)) {
                getEntityCollectorMethod = destination.getClass().getMethod("getEntityCollector", ServerLevel.class);
            }
            Object raw = getEntityCollectorMethod.invoke(destination, level);
            if (raw instanceof Optional<?> optional && optional.orElse(null) instanceof Player player) {
                return player;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 只按 Malum 精魂实体类名判定（含父类）。
     * <p>
     * 刻意不用「物品类名含 Spirit」兜底：那会把玩家丢出的精魂碎片也当成精魂，放大后即凭空复制。
     */
    public static boolean isSpiritDropEntity(Entity entity) {
        if (entity == null || entity.isRemoved() || entity instanceof MultiStackItemEntity) {
            return false;
        }
        for (Class<?> c = entity.getClass(); c != null; c = c.getSuperclass()) {
            String name = c.getName();
            if (name.equals(SPIRIT_ITEM_ENTITY)
                    || name.equals(SPIRIT_ITEM_ENTITY_ALT)
                    || name.equals(FLOATING_ITEM_ENTITY)) {
                return true;
            }
        }
        return false;
    }

    /** 成功返回 stack（可为空）；读取失败返回 null，调用方不得据此丢弃实体。 */
    public static ItemStack getEntityItem(Entity entity) {
        if (entity instanceof ItemEntity itemEntity) {
            return itemEntity.getItem();
        }
        try {
            if (getItemMethod == null || !getItemMethod.getDeclaringClass().isInstance(entity)) {
                getItemMethod = entity.getClass().getMethod("getItem");
            }
            Object result = getItemMethod.invoke(entity);
            return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Malum「待碎裂」战利品：{@code SpiritHarvestHandler.primeItemForShatter} 会 setNeverPickUp() 并
     * setNoGravity(true)，随后让其到期碎裂成精魂。这类掉落必须照常入世，否则 Malum 的精魂生成链断裂。
     * <p>
     * 判据取其两个公开可见后果（永不拾取 + 无重力）的合取：本模组/Minecraft 自有成员的官方名在生产环境
     * 会被 reobf 成 SRG 名，反射私有字段会在真实服务器上静默失效，故一律走公开 API。
     */
    public static boolean isShatterPrimedDrop(ItemEntity itemEntity) {
        return itemEntity != null
                && itemEntity.hasPickUpDelay()
                && itemEntity.isNoGravity();
    }
}
