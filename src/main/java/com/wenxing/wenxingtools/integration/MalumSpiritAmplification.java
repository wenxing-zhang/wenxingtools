package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.entity.MultiStackItemEntity;
import com.wenxing.wenxingtools.event.ResourceAmplificationHandler;
import com.wenxing.wenxingtools.util.CombatUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Malum 精魂识别 / 增幅工具。
 * <p>
 * 精魂是 Malum 的 {@code SpiritItemEntity}（{@code FloatingItemEntity} 子类）：它会自动飞向击杀者并被吸收，
 * <b>不是</b>普通掉落物，也不应被转成落地物品——否则玩家只能靠碰撞拾取，且并入/丢弃过程会丢精魂。
 * <p>
 * 入世时按击杀者当前的资源增幅状态处理（无增幅不动 / 有增幅 ×{@value #SPIRIT_MULTIPLIER}）：
 * <ul>
 *   <li><b>新生成</b>的精魂：直接把放大后的数量交给击杀者——走 Malum 的 {@code pickupSpirit} 入账
 *       （保留精魂袋路由 / 收集事件 / 溢出落地语义），成功即移除实体，达成「立即到手」；任一步失败回落为放大实体。</li>
 *   <li><b>从磁盘重载</b>的精魂：只放大实体所载数量、保留实体交回 Malum 自动收集，避免重登时重复入账。</li>
 * </ul>
 * 反射仅用于 Malum 侧类型（第三方模组不参与本仓库的映射改名，源码名即运行时名）；
 * 反射失败一律保守降级（×1 / 保留实体）。
 * <p>
 * 版本适配：入账入口在 Malum 1.21.1 为 {@code com.sammy.malum.core.handlers.SoulHarvestHandler}
 * （1.20.1 侧为 {@code SpiritHarvestHandler}）——本仓库对齐 NeoForge 1.21.1，故取前者。
 */
public final class MalumSpiritAmplification {

    /** 精魂资源增幅固定倍率（与掉落物的 (1+level×100) 不同） */
    public static final int SPIRIT_MULTIPLIER = 10;

    /** 放大标记：写在精魂物品的 CUSTOM_DATA 上，随实体存档往返，防止区块重载再次触发入世事件导致重复放大 */
    private static final String AMP_KEY = WenXingTools.MODID + ":spirit_amp";

    private static final String SPIRIT_ITEM_ENTITY = "com.sammy.malum.common.entity.spirit.SpiritItemEntity";
    private static final String SPIRIT_ITEM_ENTITY_ALT = "com.sammy.malum.common.entity.SpiritItemEntity";
    private static final String FLOATING_ITEM_ENTITY = "com.sammy.malum.common.entity.FloatingItemEntity";
    private static final String SOUL_HARVEST_HANDLER = "com.sammy.malum.core.handlers.SoulHarvestHandler";

    private static Method getItemMethod;
    private static Method setItemMethod;
    private static Method getDestinationMethod;
    private static Method getEntityCollectorMethod;
    private static Method pickupSpiritMethod;

    private MalumSpiritAmplification() {
    }

    public static void register() {
        // 精魂入世：新生成的直接交给击杀者，磁盘重载的只放大实体数量
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                MalumSpiritAmplification::onEntityJoin);
        WenXingTools.LOGGER.debug("[resource_amp] Malum spirit helpers registered (amp x{})", SPIRIT_MULTIPLIER);
    }

    /** 精魂入世：新生成的直接交给击杀者，磁盘重载的只放大实体数量。 */
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity == null || entity.isRemoved() || !isSpiritDropEntity(entity)) {
            return;
        }
        Player owner = resolveSpiritOwner(serverLevel, entity);
        if (owner == null) {
            return;
        }
        int multiplier = resolveSpiritMultiplier(owner);
        if (multiplier <= 1) {
            return;
        }
        // 新生成的精魂直接交付；磁盘重载的保留实体，避免重登时重复入账
        boolean deliverDirectly = !event.loadedFromDisk();
        // 延到下一刻，避免在入世事件里改动刚入世的实体
        serverLevel.getServer().execute(() -> handleSpirit(entity, owner, multiplier, deliverDirectly));
    }

    private static void handleSpirit(Entity entity, Player owner, int multiplier, boolean deliverDirectly) {
        if (entity.isRemoved()) {
            return;
        }
        ItemStack current = getEntityItem(entity);
        if (current == null || current.isEmpty()) {
            return;
        }
        // 已处理过（区块重载会再次触发入世事件）：跳过，避免指数级放大
        CustomData data = current.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (data.contains(AMP_KEY)) {
            return;
        }
        long amplified = (long) current.getCount() * multiplier;
        int count = (int) Math.min(amplified, Integer.MAX_VALUE);
        if (count <= current.getCount() || count <= 0) {
            return;
        }
        if (deliverDirectly && !owner.isRemoved()
                && owner.level() == entity.level()
                && deliverSpiritDirectly(owner, current, count)) {
            entity.discard();
            return;
        }
        ItemStack target = current.copy();
        target.setCount(count);
        var tag = data.copyTag();
        tag.putInt(AMP_KEY, multiplier);
        target.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (!setEntityItem(entity, target)) {
            WenXingTools.LOGGER.debug("[resource_amp] Malum spirit amplify skipped for {}", entity.getType());
        }
    }

    /** 走 Malum 的 pickupSpirit 直接入账（保留精魂袋 / 收集事件 / 溢出落地）；失败返回 false 交回放大回落。 */
    private static boolean deliverSpiritDirectly(Player owner, ItemStack current, int count) {
        ItemStack delivered = current.copy();
        delivered.setCount(count);
        try {
            if (pickupSpiritMethod == null) {
                Class<?> handler = Class.forName(SOUL_HARVEST_HANDLER);
                pickupSpiritMethod = handler.getMethod("pickupSpirit", LivingEntity.class, ItemStack.class);
            }
            pickupSpiritMethod.invoke(null, owner, delivered);
            return true;
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[resource_amp] Malum spirit direct pickup unavailable, falling back: {}", t.toString());
            return false;
        }
    }

    /** 击杀者：精魂的 collector → 在线玩家。任一环节失败返回 null（调用方保守放行）。 */
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

    private static boolean setEntityItem(Entity entity, ItemStack stack) {
        try {
            if (setItemMethod == null || !setItemMethod.getDeclaringClass().isInstance(entity)) {
                setItemMethod = entity.getClass().getMethod("setItem", ItemStack.class);
            }
            setItemMethod.invoke(entity, stack);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
