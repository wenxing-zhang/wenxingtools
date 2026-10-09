package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.entity.MultiStackItemEntity;
import com.wenxing.wenxingtools.event.AmpSupport;
import com.wenxing.wenxingtools.util.CombatUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Malum 精魂识别 / 增幅工具。
 * <p>
 * 精魂是 Malum 的 {@code SpiritItemEntity}（{@code FloatingEntity} 子类）：它会自动飞向击杀者并被吸收，
 * <b>不是</b>普通掉落物——手拾不了、磁铁也不认。它只有被 Malum 的 {@code collect()} 成功收取后才会
 * {@code remove()}；而 {@code collect()} 依赖击杀者归属：{@code ownerUUID} 为空（非玩家击杀 / 击杀者离线 /
 * 死亡点附近无玩家）时 {@code getDestination()} 返回 null，精魂既不飞也不收，<b>原地堆叠</b>到 {@code maxAge}
 * （约 40 秒）自毁——既是精魂丢失，也会因大量实体拖垮服务器。
 * <p>
 * 因此本类<b>全权接管</b>精魂的入世结算，不再依赖 Malum 自行收集：
 * <ul>
 *   <li>有击杀者归属：按击杀者当前的资源增幅状态放大数量后直接交付，随后移除实体；</li>
 *   <li>无击杀者归属（兜底）：转交给最近的玩家（按其增幅状态放大），附近确无玩家则直接移除——避免原地堆积；</li>
 *   <li>交付优先走 Malum 的 {@code pickupSpirit}（保留精魂袋路由 / 收集事件 / 溢出落地语义），该入口不可用时自主入包或落地；</li>
 *   <li>放行 Malum 打过「待碎裂」标记的战利品，避免掉落合并掐断 Malum 的「物品 → 精魂」生成链。</li>
 * </ul>
 * 反射仅用于 Malum 侧类型（第三方模组不参与 reobf，源码名即运行时名）；
 * 对本模组/Minecraft 自有成员一律用公开 API，反射失败一律保守降级（不丢精魂）。
 */
public final class MalumSpiritIntegration {

    /** 精魂资源增幅固定倍率（与掉落物的 (1+level×100) 不同） */
    public static final int SPIRIT_MULTIPLIER = 10;

    /** 无归属精魂兜底转交的搜索半径（格）；超出则视为无人接收，直接清理 */
    private static final double SALVAGE_RANGE = 64.0D;

    private static final String SPIRIT_ITEM_ENTITY = "com.sammy.malum.common.entity.SpiritItemEntity";
    private static final String SPIRIT_ITEM_ENTITY_ALT = "com.sammy.malum.common.entity.spirit.SpiritItemEntity";
    private static final String FLOATING_ITEM_ENTITY = "com.sammy.malum.common.entity.FloatingItemEntity";
    private static final String SPIRIT_HARVEST_HANDLER = "com.sammy.malum.core.handlers.SpiritHarvestHandler";

    private static boolean ownerUuidFieldResolved;
    private static Field ownerUuidField;
    private static Method getItemMethod;

    private static boolean pickupSpiritResolved;
    private static Method pickupSpiritMethod;

    private MalumSpiritIntegration() {
    }

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                MalumSpiritIntegration::onEntityJoin);
        WenXingTools.LOGGER.debug("[resource_amp] Malum spirit helpers registered (amp x{})", SPIRIT_MULTIPLIER);
    }

    /** 精魂入世：有击杀者归属则按其增幅状态放大后交付；无归属则兜底转交最近玩家，绝不让实体滞留堆积。 */
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity == null || entity.isRemoved() || !isSpiritDropEntity(entity)) {
            return;
        }
        Player owner = resolveSpiritOwner(serverLevel, entity);
        if (owner != null) {
            int multiplier = resolveSpiritMultiplier(owner);
            if (multiplier <= 1) {
                // 无资源增幅：交回 Malum 原生收集（有归属能正常飞到并吸收）
                return;
            }
            // 延到下一刻，避免在入世事件里改动刚入世的实体
            serverLevel.getServer().execute(() -> handleSpirit(entity, owner, multiplier));
            return;
        }
        // 无击杀者归属：Malum 既不会飞也不会收，精魂会原地堆叠到寿命上限。兜底转交 / 清理。
        serverLevel.getServer().execute(() -> salvageSpirit(serverLevel, entity));
    }

    private static void handleSpirit(Entity entity, Player owner, int multiplier) {
        if (entity.isRemoved() || owner.isRemoved() || owner.level() != entity.level()) {
            return;
        }
        ItemStack current = getEntityItem(entity);
        if (current == null || current.isEmpty()) {
            return;
        }
        long amplified = (long) current.getCount() * multiplier;
        int count = (int) Math.min(amplified, Integer.MAX_VALUE);
        if (count <= current.getCount() || count <= 0) {
            return;
        }
        // 交付成功即移除实体：精魂一旦入世就不再以实体形态留在世界里（否则无人收取会飘到 maxAge 自毁而丢失）
        if (deliverToOwner(owner, current, count)) {
            entity.discard();
        }
    }

    /**
     * 无归属精魂兜底：转交最近的玩家（按其当前增幅状态放大）；附近确无玩家可接收则直接移除。
     * <p>
     * 两种情况都比「原地悬浮到 maxAge 自毁」好：前者让玩家真正拿到精魂，后者立刻释放实体、避免堆积卡服。
     */
    private static void salvageSpirit(ServerLevel level, Entity entity) {
        if (entity.isRemoved()) {
            return;
        }
        ItemStack current = getEntityItem(entity);
        if (current == null || current.isEmpty()) {
            entity.discard();
            return;
        }
        Player target = nearestPlayer(level, entity);
        if (target == null) {
            WenXingTools.LOGGER.debug("[resource_amp] Malum spirit without owner dropped (no receiver): {}", entity.getType());
            entity.discard();
            return;
        }
        long amplified = (long) current.getCount() * Math.max(1, resolveSpiritMultiplier(target));
        int count = (int) Math.min(amplified, Integer.MAX_VALUE);
        if (count <= 0 || deliverToOwner(target, current, count)) {
            entity.discard();
        }
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
     * 只要返回 true，调用方就移除精魂实体——保证精魂不会以实体形态滞留。
     */
    private static boolean deliverToOwner(Player owner, ItemStack current, int count) {
        ItemStack delivered = current.copy();
        delivered.setCount(count);
        Method method = resolvePickupSpiritMethod();
        if (method != null) {
            try {
                method.invoke(null, owner, delivered);
                return true;
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
        return true;
    }

    /** Malum 精魂入账入口；缺失 / 改名时返回 null 并降级为自主交付（只提示一次）。 */
    private static Method resolvePickupSpiritMethod() {
        if (!pickupSpiritResolved) {
            pickupSpiritResolved = true;
            try {
                Class<?> handler = Class.forName(SPIRIT_HARVEST_HANDLER);
                pickupSpiritMethod = handler.getMethod("pickupSpirit", LivingEntity.class, ItemStack.class);
            } catch (Throwable t) {
                WenXingTools.LOGGER.warn("[resource_amp] Malum spirit pickup entry unavailable; using direct delivery: {}", t.toString());
            }
        }
        return pickupSpiritMethod;
    }

    /** 击杀者：读精魂的 ownerUUID → 解析在线玩家。任一环节失败返回 null（交由兜底处理）。 */
    private static Player resolveSpiritOwner(ServerLevel level, Entity spirit) {
        UUID ownerUuid = readOwnerUuid(spirit);
        if (ownerUuid == null || level.getServer() == null) {
            return null;
        }
        Player player = level.getServer().getPlayerList().getPlayer(ownerUuid);
        if (player == null || CombatUtil.isNotValidAttackerPlayer(player)) {
            return null;
        }
        return player;
    }

    /** 击杀者是否处于资源增幅：现算等级，>0 返回 ×{@value #SPIRIT_MULTIPLIER}，否则 ×1。 */
    private static int resolveSpiritMultiplier(Player player) {
        int resourceLevel = AmpSupport.resolveResourceLevel(player, null);
        return resourceLevel > 0 ? SPIRIT_MULTIPLIER : 1;
    }

    private static UUID readOwnerUuid(Entity entity) {
        try {
            if (!ownerUuidFieldResolved) {
                ownerUuidFieldResolved = true;
                try {
                    ownerUuidField = entity.getClass().getField("ownerUUID");
                } catch (Throwable t) {
                    WenXingTools.LOGGER.warn("[resource_amp] Malum spirit ownerUUID field unavailable; spirit amplification disabled: {}", t.toString());
                }
            }
            if (ownerUuidField == null) {
                return null;
            }
            Object value = ownerUuidField.get(entity);
            return value instanceof UUID uuid ? uuid : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 只按 Malum 精魂实体类名判定（含父类）。
     * <p>
     * 刻意不再用「物品类名含 Spirit」兜底：那会把玩家丢出的精魂碎片也当成精魂——放大后即凭空复制。
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
