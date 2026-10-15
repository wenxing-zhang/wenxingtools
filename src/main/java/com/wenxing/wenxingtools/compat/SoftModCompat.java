package com.wenxing.wenxingtools.compat;

import com.wenxing.wenxingtools.WenXingTools;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    private static final String TIAB_API_CLASS = "org.mangorage.tiab.common.api.ICommonTimeInABottleAPI";
    private static final String TIAB_CONFIG_CLASS = "org.mangorage.tiab.common.api.ITiabConfig";
    private static final String TIAB_ITEM_CLASS = "org.mangorage.tiab.common.api.impl.ITiabItem";
    private static final String TIAB_COMPONENT_CLASS = "org.mangorage.tiab.common.api.impl.IStoredTimeComponent";

    /** 1.21.1 的 TIAB 把瓶内时间存于自有 DataComponent（stored/total），不再读 vanilla custom_data。 */
    private static volatile Boolean tiabBridgeReady;
    private static volatile Object tiabItemRef;
    private static volatile Object tiabConfigRef;
    private static volatile Method tiabTickBottle;
    private static volatile Method tiabGetStoredComponent;
    private static volatile Method tiabStoredGetter;
    private static volatile Method tiabMaxStoredTime;

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
    /** CuriosApi 类引用：兜底扫描复用，避免每次重复 Class.forName。 */
    private static volatile Class<?> curiosApiClass;
    /** Curios 运行时类的方法查找缓存（类加载后恒定）：避免每次扫描重复 getMethod。 */
    private static final ClassValue<java.util.Map<String, Method>> METHOD_CACHE =
            new ClassValue<>() {
                @Override
                protected java.util.Map<String, Method> computeValue(Class<?> type) {
                    return new java.util.concurrent.ConcurrentHashMap<>();
                }
            };

    private static Method methodOf(Class<?> type, String name, Class<?>... params) {
        if (type == null) {
            return null;
        }
        return METHOD_CACHE.get(type).computeIfAbsent(name + java.util.Arrays.toString(params), key -> {
            try {
                return type.getMethod(name, params);
            } catch (Throwable t) {
                return null;
            }
        });
    }
    /** 注册表在模组加载后冻结：物品/属性解析结果恒定，缓存非空值避免每秒重复解析。 */
    private static final java.util.Map<String, Item> ITEM_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Holder<Attribute>> ATTR_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

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
        Item cached = ITEM_CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        try {
            ResourceLocation rl = ResourceLocation.parse(id);
            Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
            if (item != null) {
                ITEM_CACHE.put(id, item);
            }
            return item;
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
            Class<?> apiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> helperClass = Class.forName("top.theillusivec4.curios.api.ICuriosHelper");
            getCuriosHelper = apiClass.getMethod("getCuriosHelper");
            findFirstCurioItem = helperClass.getMethod("findFirstCurio", LivingEntity.class, Item.class);
            curiosApiClass = apiClass;
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
            Method getInv = methodOf(curiosApiClass, "getCuriosInventory", LivingEntity.class);
            if (getInv == null) {
                return false;
            }
            Object handler = unwrapMaybeOptional(getInv.invoke(null, entity));
            if (handler == null) {
                return false;
            }
            Method getCurios = methodOf(handler.getClass(), "getCurios");
            if (getCurios == null) {
                return false;
            }
            Object curiosMap = getCurios.invoke(handler);
            if (!(curiosMap instanceof java.util.Map<?, ?> map)) {
                return false;
            }
            for (Object stacksHandler : map.values()) {
                if (stacksHandler == null) {
                    continue;
                }
                Method getStacks = methodOf(stacksHandler.getClass(), "getStacks");
                if (getStacks == null) {
                    continue;
                }
                Object itemHandler = getStacks.invoke(stacksHandler);
                Method getSlots = methodOf(itemHandler.getClass(), "getSlots");
                Method getStackInSlot = methodOf(itemHandler.getClass(), "getStackInSlot", int.class);
                if (getSlots == null || getStackInSlot == null) {
                    continue;
                }
                int n = (Integer) getSlots.invoke(itemHandler);
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
            Method resolve = methodOf(raw.getClass(), "resolve");
            if (resolve == null) {
                return raw;
            }
            Object resolved = resolve.invoke(raw);
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
        Holder<Attribute> cached = ATTR_CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        try {
            ResourceLocation rl = ResourceLocation.parse(id);
            Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(rl).orElse(null);
            if (holder != null) {
                ATTR_CACHE.put(id, holder);
            }
            return holder;
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
            ensureTiabBridge();
            if (!Boolean.TRUE.equals(tiabBridgeReady)) {
                return;
            }
            Object component = tiabGetStoredComponent.invoke(tiabItemRef, stack);
            int stored = component == null ? 0 : (Integer) tiabStoredGetter.invoke(component);
            int delta = (Integer) tiabMaxStoredTime.invoke(tiabConfigRef) - stored;
            if (delta <= 0) {
                return;
            }
            tiabTickBottle.invoke(tiabItemRef, stack, delta);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 反射解析 TIAB 的瓶内时间组件与补齐入口。
     * 1.21.1 的 TIAB 只认自有 DataComponent；旧实现往 custom_data 写 storedTime 属于写错地方，从未生效。
     * 任一步不命中即整体关闭，保持原样不碰物品。
     */
    private static void ensureTiabBridge() {
        Boolean cached = tiabBridgeReady;
        if (cached != null) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName(TIAB_API_CLASS);
            Object supplier = apiClass.getField("COMMON_API").get(null);
            Object api = java.util.function.Supplier.class.getMethod("get").invoke(supplier);
            Object config = apiClass.getMethod("getConfig").invoke(api);
            Item item = itemOrNull(TIAB_ID);
            if (api == null || config == null || item == null) {
                tiabBridgeReady = false;
                return;
            }
            Class<?> itemClass = Class.forName(TIAB_ITEM_CLASS);
            tiabConfigRef = config;
            tiabItemRef = item;
            tiabTickBottle = itemClass.getMethod("tickBottle", ItemStack.class, int.class);
            tiabGetStoredComponent = itemClass.getMethod("getStoredComponent", ItemStack.class);
            tiabStoredGetter = Class.forName(TIAB_COMPONENT_CLASS).getMethod("stored");
            tiabMaxStoredTime = Class.forName(TIAB_CONFIG_CLASS).getMethod("MAX_STORED_TIME");
            tiabBridgeReady = true;
        } catch (Throwable t) {
            WenXingTools.LOGGER.debug("[compat] TIAB stored-time bridge unavailable: {}", t.toString());
            tiabBridgeReady = false;
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
