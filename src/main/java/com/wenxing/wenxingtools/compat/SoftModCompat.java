package com.wenxing.wenxingtools.compat;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.Holder;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 可选模组软加载（1.21.1 / NeoForge）：Curios / Iron's Spells / Time in a Bottle。
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

    private static final ResourceLocation CROWN_MANA_ID =
            ResourceLocation.fromNamespaceAndPath(WenXingTools.MODID, "gold_crown_mana");
    private static final ResourceLocation CROWN_CDR_ID =
            ResourceLocation.fromNamespaceAndPath(WenXingTools.MODID, "gold_crown_cdr");
    private static final ResourceLocation CROWN_SP_ID =
            ResourceLocation.fromNamespaceAndPath(WenXingTools.MODID, "gold_crown_sp");

    private static final String PERSIST_TIAB_STARTER = "wenxingtools_tiab_starter";
    private static final String PERSIST_HEAD_SLOT_GIFT = "wenxingtools_head_slot_gift";

    private static volatile Boolean curiosReady;
    private static volatile Method getCuriosHelper;
    private static volatile Method findFirstCurioItem;

    private SoftModCompat() {
    }

    public static boolean isLoaded(String modId) {
        try {
            return net.neoforged.fml.ModList.get().isLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    public static Item itemOrNull(String id) {
        try {
            ResourceLocation rl = ResourceLocation.parse(id);
            return BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
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
    // Curios
    // ----------------------------------------------------------

    /** 旧版 findFirstCurio 可选；缺失时只走背包扫描，不能把扫描短路掉。 */
    private static void ensureCurios() {
        Boolean cached = curiosReady;
        if (cached != null) {
            return;
        }
        try {
            Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> helperClass = Class.forName("top.theillusivec4.curios.api.ICuriosHelper");
            getCuriosHelper = curiosApiClass.getMethod("getCuriosHelper");
            findFirstCurioItem = helperClass.getMethod("findFirstCurio", LivingEntity.class, Item.class);
            curiosReady = true;
        } catch (Throwable t) {
            WenXingTools.LOGGER.debug("[compat] Curios findFirstCurio unavailable, scan-only: {}", t.toString());
            curiosReady = false;
        }
    }

    public static boolean hasItemInCurios(LivingEntity entity, String itemId) {
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
                if (optional instanceof Optional<?> opt && opt.isPresent()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return scanCuriosInventoryFor(entity, item);
    }

    /** 兜底：遍历 Curios 格子，避免 findFirstCurio 不匹配或 API 变更时永远 false。 */
    private static boolean scanCuriosInventoryFor(LivingEntity entity, Item item) {
        try {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getInv = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
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
        if (raw instanceof Optional<?> opt) {
            return opt.orElse(null);
        }
        try {
            Object resolved = raw.getClass().getMethod("resolve").invoke(raw);
            if (resolved instanceof Optional<?> opt) {
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
                int result = server.getCommands().getDispatcher().execute(
                        "curios add " + name + " head 1",
                        server.createCommandSourceStack().withSuppressedOutput());
                if (result > 0) {
                    player.getPersistentData().putBoolean(PERSIST_HEAD_SLOT_GIFT, true);
                }
            } catch (Throwable t) {
                WenXingTools.LOGGER.debug("[compat] curios add head failed: {}", t.toString());
            }
        });
    }

    // ----------------------------------------------------------
    // 金冠 Curios 属性
    // ----------------------------------------------------------

    private static Holder<Attribute> attrHolder(String id) {
        try {
            ResourceLocation rl = ResourceLocation.parse(id);
            return BuiltInRegistries.ATTRIBUTE.getHolder(rl).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void applyModifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id,
                                      double amount, AttributeModifier.Operation op) {
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
        instance.addPermanentModifier(new AttributeModifier(id, amount, op));
    }

    private static void removeModifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id) {
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

    private static boolean hasModifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id) {
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
            Holder<Attribute> mana = attrHolder("irons_spellbooks:max_mana");
            Holder<Attribute> cdr = attrHolder("irons_spellbooks:cooldown_reduction");
            Holder<Attribute> sp = attrHolder("irons_spellbooks:spell_power");
            boolean has = hasItemInCurios(player, GOLD_CROWN_ID);
            boolean manaMod = hasModifier(player, mana, CROWN_MANA_ID);
            boolean cdrMod = hasModifier(player, cdr, CROWN_CDR_ID);
            boolean spMod = hasModifier(player, sp, CROWN_SP_ID);

            if (has && !(manaMod && cdrMod && spMod)) {
                applyModifier(player, mana, CROWN_MANA_ID,
                        10000.0D, AttributeModifier.Operation.ADD_VALUE);
                applyModifier(player, cdr, CROWN_CDR_ID,
                        0.75D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                applyModifier(player, sp, CROWN_SP_ID,
                        1.00D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
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
    // 时间之瓶
    // ----------------------------------------------------------

    public static void fillTiabTime(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isItem(stack, TIAB_ID)) {
            return;
        }
        try {
            CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            var tag = data.copyTag();
            if (tag.getInt(NBT_STORED_TIME) == MAX_TIME && tag.getInt(NBT_TOTAL_TIME) == MAX_TIME) {
                return;
            }
            // 必须是 int：TIAB 读 getInt；同键写 long 会变成 TAG_LONG 导致读到 0
            tag.putInt(NBT_STORED_TIME, MAX_TIME);
            tag.putInt(NBT_TOTAL_TIME, MAX_TIME);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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
        } catch (Throwable ignored) {
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
}
