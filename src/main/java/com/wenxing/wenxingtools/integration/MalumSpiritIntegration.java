package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.entity.MultiStackItemEntity;
import com.wenxing.wenxingtools.event.AmpSupport;
import com.wenxing.wenxingtools.util.CombatUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
 * 本类只做两件事，不改写精魂实体形态：
 * <ul>
 *   <li>入世时按击杀者当前的资源增幅状态放大精魂数量（无增幅 ×1 / 有增幅 ×{@value #SPIRIT_MULTIPLIER}）；</li>
 *   <li>放行 Malum 打过「待碎裂」标记的战利品，避免掉落合并掐断 Malum 的「物品 → 精魂」生成链。</li>
 * </ul>
 * 反射仅用于 Malum 侧类型（第三方模组不参与 reobf，源码名即运行时名）；
 * 对本模组/Minecraft 自有成员一律用公开 API，反射失败一律保守降级（×1 / 不放行）。
 */
public final class MalumSpiritIntegration {

    /** 精魂资源增幅固定倍率（与掉落物的 (1+level×100) 不同） */
    public static final int SPIRIT_MULTIPLIER = 10;

    /** 放大标记：既让 FloatingItemEntity#setItem 的默认物品守卫放行，又防止区块重载时重复放大 */
    private static final String AMP_TAG = WenXingTools.MODID + ":spirit_amp";

    private static final String SPIRIT_ITEM_ENTITY = "com.sammy.malum.common.entity.SpiritItemEntity";
    private static final String SPIRIT_ITEM_ENTITY_ALT = "com.sammy.malum.common.entity.spirit.SpiritItemEntity";
    private static final String FLOATING_ITEM_ENTITY = "com.sammy.malum.common.entity.FloatingItemEntity";

    private static boolean ownerUuidFieldResolved;
    private static Field ownerUuidField;
    private static Method getItemMethod;
    private static Method setItemMethod;

    private MalumSpiritIntegration() {
    }

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                MalumSpiritIntegration::onEntityJoin);
        WenXingTools.LOGGER.debug("[resource_amp] Malum spirit helpers registered (amp x{})", SPIRIT_MULTIPLIER);
    }

    /** 精魂入世：按击杀者增幅状态放大数量，保留实体本身交回 Malum 自动收集。 */
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity == null || entity.isRemoved() || !isSpiritDropEntity(entity)) {
            return;
        }
        int multiplier = resolveSpiritMultiplier(serverLevel, entity);
        if (multiplier <= 1) {
            return;
        }
        // 延到下一刻，避免在入世事件里改动刚入世的实体
        serverLevel.getServer().execute(() -> amplifySpirit(entity, multiplier));
    }

    private static void amplifySpirit(Entity entity, int multiplier) {
        if (entity.isRemoved()) {
            return;
        }
        ItemStack current = getEntityItem(entity);
        if (current == null || current.isEmpty()) {
            return;
        }
        // 已放大过（区块重载会再次触发入世事件）：跳过，避免指数级放大
        if (current.hasTag() && current.getTag().contains(AMP_TAG)) {
            return;
        }
        long amplified = (long) current.getCount() * multiplier;
        int count = (int) Math.min(amplified, Integer.MAX_VALUE);
        if (count <= current.getCount() || count <= 0) {
            return;
        }
        ItemStack target = current.copy();
        target.setCount(count);
        target.getOrCreateTag().putInt(AMP_TAG, multiplier);
        if (!setEntityItem(entity, target)) {
            WenXingTools.LOGGER.debug("[resource_amp] Malum spirit amplify skipped for {}", entity.getType());
        }
    }

    /** 击杀者是否处于资源增幅：读精魂的 ownerUUID → 解析玩家 → 现算等级。任一环节失败一律 ×1。 */
    private static int resolveSpiritMultiplier(ServerLevel level, Entity spirit) {
        UUID ownerUuid = readOwnerUuid(spirit);
        if (ownerUuid == null || level.getServer() == null) {
            return 1;
        }
        Player player = level.getServer().getPlayerList().getPlayer(ownerUuid);
        if (player == null || CombatUtil.isNotValidAttackerPlayer(player)) {
            return 1;
        }
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
