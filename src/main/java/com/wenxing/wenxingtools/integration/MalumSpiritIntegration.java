package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.entity.MultiStackItemEntity;
import com.wenxing.wenxingtools.event.AmpSupport;
import com.wenxing.wenxingtools.util.CombatUtil;
import net.minecraft.server.level.ServerLevel;
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
 * {@code remove()}，否则会一直飘着，直到 {@code maxAge}（约 40 秒）自毁，等于精魂丢失。
 * <p>
 * 因此本类改为<b>全权接管</b>：精魂入世时按击杀者当前的资源增幅状态放大数量后<b>直接交付</b>，随后<b>必定移除</b>
 * 精魂实体——不再依赖 Malum 自行收集，也不再把实体交回给它：
 * <ul>
 *   <li>优先走 Malum 的 {@code pickupSpirit}（保留精魂袋路由 / 收集事件 / 溢出落地语义）；</li>
 *   <li>该入口不可用或抛错时自主交付——先入背包，仍放不下则落地成普通 {@code ItemEntity}（可拾取、可被磁铁吸取）；</li>
 *   <li>放行 Malum 打过「待碎裂」标记的战利品，避免掉落合并掐断 Malum 的「物品 → 精魂」生成链。</li>
 * </ul>
 * 反射仅用于 Malum 侧类型（第三方模组不参与 reobf，源码名即运行时名）；
 * 对本模组/Minecraft 自有成员一律用公开 API，反射失败一律保守降级（不丢精魂）。
 */
public final class MalumSpiritIntegration {

    /** 精魂资源增幅固定倍率（与掉落物的 (1+level×100) 不同） */
    public static final int SPIRIT_MULTIPLIER = 10;

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

    /** 精魂入世：按击杀者增幅状态放大后直接交付并移除实体，避免精魂滞留飘落。 */
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
        // 延到下一刻，避免在入世事件里改动刚入世的实体
        serverLevel.getServer().execute(() -> handleSpirit(entity, owner, multiplier));
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
     * 交付给击杀者：优先走 Malum 的 pickupSpirit（保留精魂袋 / 收集事件 / 溢出落地语义）；
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

    /** 击杀者：读精魂的 ownerUUID → 解析在线玩家。任一环节失败返回 null（调用方保守放行，交给 Malum）。 */
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
