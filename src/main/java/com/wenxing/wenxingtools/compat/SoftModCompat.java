package com.wenxing.wenxingtools.compat;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 可选模组软加载工具：Curios / Iron's Spells / Time in a Bottle。
 * <p>
 * 全程反射 + ModList，相关模组缺失时功能跳过，不导致进不了游戏。
 */
public final class SoftModCompat {

    public static final String CURIOS = "curios";
    public static final String IRONS = "irons_spellbooks";
    public static final String TIAB = "tiab";

    public static final String GOLD_CROWN_ID = "irons_spellbooks:gold_crown";
    public static final String TIAB_ID = "tiab:time_in_a_bottle";

    public static final String NBT_STORED_TIME = "storedTime";
    public static final String NBT_TOTAL_TIME = "totalAccumulatedTime";
    public static final int MAX_TIME = Integer.MAX_VALUE;

    private static final UUID CROWN_MANA_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567001");
    private static final UUID CROWN_CDR_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567002");
    private static final UUID CROWN_SP_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567003");

    private static final String PERSIST_TIAB_STARTER = "wenxingtools_tiab_starter";
    private static final String PERSIST_HEAD_SLOT_GIFT = "wenxingtools_head_slot_gift";

    private static volatile Boolean curiosReady;
    private static volatile Class<?> curiosApiClass;
    private static volatile Method getCuriosHelper;
    private static volatile Method findFirstCurioItem;

    private SoftModCompat() {
    }

    public static boolean isLoaded(String modId) {
        try {
            return net.minecraftforge.fml.ModList.get().isLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    public static Item itemOrNull(String id) {
        try {
            ResourceLocation rl = new ResourceLocation(id);
            return ForgeRegistries.ITEMS.getValue(rl);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isItem(ItemStack stack, String itemId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = itemOrNull(itemId);
        return item != null && stack.is(item);
    }

    // ----------------------------------------------------------
    // Curios：判断物品是否在饰品栏
    // ----------------------------------------------------------

    /** 旧版 findFirstCurio 可选；缺失时只走背包扫描，不能把扫描短路掉。 */
    private static void ensureCurios() {
        Boolean cached = curiosReady;
        if (cached != null) {
            return;
        }
        try {
            curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> helperClass = Class.forName("top.theillusivec4.curios.api.ICuriosHelper");
            getCuriosHelper = curiosApiClass.getMethod("getCuriosHelper");
            findFirstCurioItem = helperClass.getMethod("findFirstCurio",
                    net.minecraft.world.entity.LivingEntity.class, Item.class);
            curiosReady = true;
        } catch (Throwable t) {
            WenXingTools.LOGGER.debug("[compat] Curios findFirstCurio unavailable, scan-only: {}", t.toString());
            curiosReady = false;
        }
    }

    public static boolean hasItemInCurios(net.minecraft.world.entity.LivingEntity entity, String itemId) {
        if (entity == null || !isLoaded(CURIOS)) {
            return false;
        }
        Item item = itemOrNull(itemId);
        if (item == null) {
            return false;
        }
        ensureCurios();
        if (Boolean.TRUE.equals(curiosReady)) {
            try {
                Object helper = getCuriosHelper.invoke(null);
                Object optional = findFirstCurioItem.invoke(helper, entity, item);
                if (optional instanceof java.util.Optional<?> opt && opt.isPresent()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return scanCuriosInventoryFor(entity, item);
    }

    /** 兜底：遍历 Curios 格子，避免 findFirstCurio 不匹配或 API 变更时永远 false。 */
    private static boolean scanCuriosInventoryFor(net.minecraft.world.entity.LivingEntity entity, Item item) {
        try {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getInv = curiosApi.getMethod("getCuriosInventory",
                    net.minecraft.world.entity.LivingEntity.class);
            Object handler = unwrapMaybeOptional(getInv.invoke(null, entity));
            if (handler == null) {
                return false;
            }
            Object curiosMap = handler.getClass().getMethod("getCurios").invoke(handler);
            if (!(curiosMap instanceof java.util.Map<?, ?> map)) {
                return false;
            }
            for (Object stacksHandler : map.values()) {
                if (stacksHandler == null) {
                    continue;
                }
                Object itemHandler = stacksHandler.getClass().getMethod("getStacks").invoke(stacksHandler);
                int n = (Integer) itemHandler.getClass().getMethod("getSlots").invoke(itemHandler);
                Method getStackInSlot = itemHandler.getClass().getMethod("getStackInSlot", int.class);
                for (int i = 0; i < n; i++) {
                    Object stackObj = getStackInSlot.invoke(itemHandler, i);
                    if (stackObj instanceof ItemStack stack && !stack.isEmpty() && stack.is(item)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Curios 各版本可能返回 Optional / LazyOptional / 直接对象。 */
    private static Object unwrapMaybeOptional(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof java.util.Optional<?> opt) {
            return opt.orElse(null);
        }
        try {
            Object resolved = raw.getClass().getMethod("resolve").invoke(raw);
            if (resolved instanceof java.util.Optional<?> opt) {
                return opt.orElse(null);
            }
            return resolved;
        } catch (Throwable t) {
            return raw;
        }
    }

    /** 首次进服为玩家增加 1 格 head 饰品位（不叠加）。延到下一 tick 执行，避免登录事件内跑命令。 */
    public static void ensureHeadSlot(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null || !isLoaded(CURIOS)) {
            return;
        }
        if (player.getPersistentData().getBoolean(PERSIST_HEAD_SLOT_GIFT)) {
            return;
        }
        server.execute(() -> {
            if (player.isRemoved()) {
                return;
            }
            try {
                String name = player.getGameProfile().getName();
                int result = server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack().withSuppressedOutput(),
                        "curios add " + name + " head 1");
                if (result > 0) {
                    player.getPersistentData().putBoolean(PERSIST_HEAD_SLOT_GIFT, true);
                }
            } catch (Throwable t) {
                WenXingTools.LOGGER.debug("[compat] curios add head failed: {}", t.toString());
            }
        });
    }

    // ----------------------------------------------------------
    // 金冠在 Curios 栏时挂属性
    // ----------------------------------------------------------

    private static Attribute attr(String id) {
        try {
            return ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(id));
        } catch (Throwable t) {
            return null;
        }
    }

    private static void applyModifier(ServerPlayer player, Attribute attribute, UUID id,
                                      String name, double amount, AttributeModifier.Operation op) {
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null) {
            instance.removeModifier(existing);
        }
        instance.addPermanentModifier(new AttributeModifier(id, name, amount, op));
    }

    private static void removeModifier(ServerPlayer player, Attribute attribute, UUID id) {
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null) {
            instance.removeModifier(existing);
        }
    }

    private static boolean hasModifier(ServerPlayer player, Attribute attribute, UUID id) {
        if (attribute == null) {
            return false;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        return instance != null && instance.getModifier(id) != null;
    }

    /**
     * 以属性修饰符是否真实存在为准（不信任持久化标记）。
     * 死亡重生后永久修饰符会丢，这里每秒对账可自动补回。
     */
    public static void syncGoldCrownCuriosBonus(ServerPlayer player) {
        if (player == null || !isLoaded(IRONS) || !isLoaded(CURIOS)) {
            return;
        }
        try {
            Attribute mana = attr("irons_spellbooks:max_mana");
            Attribute cdr = attr("irons_spellbooks:cooldown_reduction");
            Attribute sp = attr("irons_spellbooks:spell_power");
            boolean has = hasItemInCurios(player, GOLD_CROWN_ID);
            boolean manaMod = hasModifier(player, mana, CROWN_MANA_ID);
            boolean cdrMod = hasModifier(player, cdr, CROWN_CDR_ID);
            boolean spMod = hasModifier(player, sp, CROWN_SP_ID);

            if (has && !(manaMod && cdrMod && spMod)) {
                applyModifier(player, mana, CROWN_MANA_ID,
                        "wenxingtools_gold_crown_mana", 10000.0D, AttributeModifier.Operation.ADDITION);
                applyModifier(player, cdr, CROWN_CDR_ID,
                        "wenxingtools_gold_crown_cdr", 0.75D, AttributeModifier.Operation.MULTIPLY_TOTAL);
                applyModifier(player, sp, CROWN_SP_ID,
                        "wenxingtools_gold_crown_sp", 1.00D, AttributeModifier.Operation.MULTIPLY_TOTAL);
            } else if (!has && (manaMod || cdrMod || spMod)) {
                removeModifier(player, mana, CROWN_MANA_ID);
                removeModifier(player, cdr, CROWN_CDR_ID);
                removeModifier(player, sp, CROWN_SP_ID);
            }
        } catch (Throwable t) {
            WenXingTools.LOGGER.debug("[compat] gold crown sync failed: {}", t.toString());
        }
    }

    // ----------------------------------------------------------
    // 时间之瓶：无限时间 + 首次赠送
    // ----------------------------------------------------------

    public static void fillTiabTime(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isItem(stack, TIAB_ID)) {
            return;
        }
        try {
            var tag = stack.getOrCreateTag();
            if (tag.getInt(NBT_STORED_TIME) == MAX_TIME && tag.getInt(NBT_TOTAL_TIME) == MAX_TIME) {
                return;
            }
            tag.putInt(NBT_STORED_TIME, MAX_TIME);
            tag.putInt(NBT_TOTAL_TIME, MAX_TIME);
        } catch (Throwable ignored) {
        }
    }

    public static void refreshPlayerTiab(ServerPlayer player) {
        if (player == null || !isLoaded(TIAB)) {
            return;
        }
        try {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                fillTiabTime(inv.getItem(i));
            }
            fillTiabTime(player.getOffhandItem());
        } catch (Throwable t) {
            // ignore
        }
    }

    public static void giveStarterTiabOnce(ServerPlayer player) {
        if (player == null || !isLoaded(TIAB)) {
            return;
        }
        if (player.getPersistentData().getBoolean(PERSIST_TIAB_STARTER)) {
            return;
        }
        Item item = itemOrNull(TIAB_ID);
        if (item == null) {
            return;
        }
        try {
            ItemStack stack = new ItemStack(item);
            fillTiabTime(stack);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            player.getPersistentData().putBoolean(PERSIST_TIAB_STARTER, true);
        } catch (Throwable t) {
            WenXingTools.LOGGER.warn("[compat] TIAB starter failed: {}", t.toString());
        }
    }

    /**
     * 一次性发放标记存于玩家 ForgeData；死亡重生会重建玩家实体，而 Forge 补丁不在 restoreFrom/clone
     * 里复制 ForgeData（只在实体存档往返时读写），故标记会在重生后丢失、导致重登重复发放，需在 Clone 事件搬运。
     */
    public static void copyPersistentGiftFlags(net.minecraft.world.entity.Entity from,
                                               net.minecraft.world.entity.Entity to) {
        if (from == null || to == null) {
            return;
        }
        CompoundTag source = from.getPersistentData();
        CompoundTag target = to.getPersistentData();
        for (String key : new String[]{PERSIST_TIAB_STARTER, PERSIST_HEAD_SLOT_GIFT}) {
            if (source.getBoolean(key)) {
                target.putBoolean(key, true);
            }
        }
    }
}
