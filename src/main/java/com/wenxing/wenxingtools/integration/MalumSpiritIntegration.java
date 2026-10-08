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
 * <b>不是</b>普通掉落物，也不应被转成落地物品——否则玩家只能靠碰撞拾取，且并入/丢弃过程会丢精魂。
 * <p>
 * 入世时按击杀者当前的资源增幅状态处理（无增幅不动 / 有增幅 ×{@value #SPIRIT_MULTIPLIER}）：
 * <ul>
 *   <li><b>新生成</b>的精魂：直接把放大后的数量交给击杀者——走 Malum 的 {@code pickupSpirit} 入账
 *       （保留精魂袋路由 / 收集事件 / 溢出落地语义），成功即移除实体，达成「立即到手」；任一步失败回落为放大实体。</li>
 *   <li><b>从磁盘重载</b>的精魂：只放大实体所载数量、保留实体交回 Malum 自动收集，避免重登时重复入账。</li>
 *   <li>放行 Malum 打过「待碎裂」标记的战利品，避免掉落合并掐断 Malum 的「物品 → 精魂」生成链。</li>
 * </ul>
 * 反射仅用于 Malum 侧类型（第三方模组不参与 reobf，源码名即运行时名）；
 * 对本模组/Minecraft 自有成员一律用公开 API，反射失败一律保守降级（×1 / 保留实体）。
 */
public final class MalumSpiritIntegration {

    /** 精魂资源增幅固定倍率（与掉落物的 (1+level×100) 不同） */
    public static final int SPIRIT_MULTIPLIER = 10;

    /** 放大标记：既让 FloatingItemEntity#setItem 的默认物品守卫放行，又防止区块重载时重复放大 */
    private static final String AMP_TAG = WenXingTools.MODID + ":spirit_amp";

    private static final String SPIRIT_ITEM_ENTITY = "com.sammy.malum.common.entity.SpiritItemEntity";
    private static final String SPIRIT_ITEM_ENTITY_ALT = "com.sammy.malum.common.entity.spirit.SpiritItemEntity";
    private static final String FLOATING_ITEM_ENTITY = "com.sammy.malum.common.entity.FloatingItemEntity";
    private static final String SPIRIT_HARVEST_HANDLER = "com.sammy.malum.core.handlers.SpiritHarvestHandler";

    private static boolean ownerUuidFieldResolved;
    private static Field ownerUuidField;
    private static Method getItemMethod;
    private static Method setItemMethod;
    private static Method pickupSpiritMethod;

    private MalumSpiritIntegration() {
    }

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                MalumSpiritIntegration::onEntityJoin);
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
        if (current.hasTag() && current.getTag().contains(AMP_TAG)) {
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
        target.getOrCreateTag().putInt(AMP_TAG, multiplier);
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
                Class<?> handler = Class.forName(SPIRIT_HARVEST_HANDLER);
                pickupSpiritMethod = handler.getMethod("pickupSpirit", LivingEntity.class, ItemStack.class);
            }
            pickupSpiritMethod.invoke(null, owner, delivered);
            return true;
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[resource_amp] Malum spirit direct pickup unavailable, falling back: {}", t.toString());
            return false;
        }
    }

    /** 击杀者：读精魂的 ownerUUID → 解析在线玩家。任一环节失败返回 null（调用方保守放行）。 */
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
