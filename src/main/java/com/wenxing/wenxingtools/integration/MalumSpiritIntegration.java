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
 * 精魂是 Malum 的 {@code SpiritItemEntity}（{@code FloatingEntity} 子类），会飞向击杀者并被吸收——<b>不是</b>
 * 普通掉落物：手拾不了、磁铁也不认。它的飞行与收取都依赖击杀者归属，归属为空时既飞不动也收不了，只会原地
 * 堆叠到 {@code maxAge}（约 40 秒）自毁。而「先让它入世、再 discard」的做法会踩到一个更糟的坑：实体在生成包
 * 发出前就被移除，服务端会为「已移除的实体」发追踪包（{@code ServerEntity: Fetching packet for removed entity}），
 * 客户端据此造出服务端已不再跟踪、也收不到销毁包的幽灵实体，在生成点越堆越多。
 * <p>
 * 因此本类改为在 {@link EntityJoinLevelEvent} 上<b>直接取消精魂入世</b>（取消后实体不会被加入世界），把放大后的
 * 物品立刻交付给玩家——实体根本不存在，自然没有追踪包、没有幽灵、没有堆积：
 * <ul>
 *   <li>有击杀者归属 + 开增幅：×{@value #SPIRIT_MULTIPLIER} 后交付；</li>
 *   <li>无击杀者归属：转交最近的玩家（按其增幅状态放大）；附近确无玩家则直接取消（原本也只能原地自毁）；</li>
 *   <li>有归属但未开增幅：不拦截，交回 Malum 原生飞行收集；</li>
 *   <li>随区块重载重新入世（{@code loadedFromDisk}）的精魂不拦截——取消会让它在每次区块加载时被重复投递。</li>
 * </ul>
 * 交付优先走 Malum 的 {@code pickupSpirit}（保留精魂袋路由 / 收集事件 / 溢出落地语义），该入口不可用时自主入包或落地。
 * 反射仅用于 Malum 侧类型（第三方模组不参与 reobf，源码名即运行时名）；反射失败一律保守降级（不丢精魂）。
 */
public final class MalumSpiritIntegration {

    /** 精魂资源增幅固定倍率（与掉落物的 (1+level×100) 不同） */
    public static final int SPIRIT_MULTIPLIER = 10;

    /** 无归属精魂兜底转交的搜索半径（格）；超出则视为无人接收，直接取消入世 */
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

        // 取消入世：实体不会进入世界，因此不会有生成包 / 追踪 / 幽灵实体，也不会堆积
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
                Class<?> handler = Class.forName(SPIRIT_HARVEST_HANDLER);
                pickupSpiritMethod = handler.getMethod("pickupSpirit", LivingEntity.class, ItemStack.class);
            } catch (Throwable t) {
                WenXingTools.LOGGER.warn("[resource_amp] Malum spirit pickup entry unavailable; using direct delivery: {}", t.toString());
            }
        }
        return pickupSpiritMethod;
    }

    /** 击杀者：读精魂的 ownerUUID → 解析在线玩家。任一环节失败返回 null（交由就近兜底）。 */
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
