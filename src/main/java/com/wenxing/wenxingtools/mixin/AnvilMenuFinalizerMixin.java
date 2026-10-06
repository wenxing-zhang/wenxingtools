package com.wenxing.wenxingtools.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 铁砧兜底：任意附魔书合并；结果槽非空时经验固定 1 级（H1 设计，不做限制）。
 * 以原版 createResult 结果为基座（保留改名/其它模组结果）；无有效合并时不清空结果、不吞书。
 * <p>
 * 父类 ItemCombinerMenu 的 inputSlots/resultSlots <b>不能</b>用 @Shadow：
 * Mixin 只解析目标类 AnvilMenu 自身声明的字段，父类字段会 InvalidMixinException（已实机踩坑）。
 * 因此用可控反射 + MixinSelfCheck 告警/关特性。
 */
@Mixin(value = AnvilMenu.class, priority = 500)
public abstract class AnvilMenuFinalizerMixin {
    @Shadow
    @Final
    private DataSlot cost;

    /** 原版 AnvilMenu#itemName（改名输入） */
    @Shadow
    @Nullable
    private String itemName;

    private static final Field F_inputSlots;
    private static final Field F_resultSlots;

    static {
        Class<?> target = null;
        try {
            target = Class.forName("net.minecraft.world.inventory.ItemCombinerMenu");
        } catch (ClassNotFoundException ignored) {
        }
        F_inputSlots = resolveField(target, "inputSlots");
        F_resultSlots = resolveField(target, "resultSlots");
        if (F_inputSlots == null || F_resultSlots == null) {
            com.wenxing.wenxingtools.WenXingTools.LOGGER.warn(
                    "[wenxingtools] AnvilMenuFinalizerMixin could not resolve ItemCombinerMenu fields (inputSlots={}, resultSlots={}); anvil enchant fallback will not run",
                    F_inputSlots != null,
                    F_resultSlots != null);
        }
    }

    private static Field resolveField(Class<?> clazz, String name) {
        if (clazz == null) {
            return null;
        }
        try {
            Field f = clazz.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Field field, Object instance) {
        if (field == null) {
            return null;
        }
        try {
            return (T) field.get(instance);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private Container getInputSlots() {
        return getField(F_inputSlots, this);
    }

    private ResultContainer getResultSlots() {
        return getField(F_resultSlots, this);
    }

    @Inject(method = "createResult", at = @At("RETURN"), require = 0)
    private void finalizeAnvilResultAfterAllMods(CallbackInfo ci) {
        if (!com.wenxing.wenxingtools.util.MixinSelfCheck.isAnvilFallbackEnabled()) {
            return;
        }
        applyFallbackForAnyEnchantment();
        applyFixedExperienceCostForcefully();
    }

    private void applyFallbackForAnyEnchantment() {
        Container inputSlots = getInputSlots();
        ResultContainer resultSlots = getResultSlots();
        if (inputSlots == null || resultSlots == null) {
            return;
        }

        ItemStack leftStack = inputSlots.getItem(0);
        ItemStack rightStack = inputSlots.getItem(1);
        if (leftStack.isEmpty() || rightStack.isEmpty()) {
            return;
        }
        if (leftStack.getItem() == Items.ENCHANTED_BOOK) {
            return;
        }
        if (rightStack.getItem() != Items.ENCHANTED_BOOK) {
            return;
        }
        if (leftStack.getCount() != 1) {
            return;
        }

        Map<Holder<Enchantment>, Integer> bookEnchants = readStoredEnchantments(rightStack);
        if (bookEnchants.isEmpty()) {
            return;
        }

        // 原版结果非空：Redirect 已放行合并，直接信任，禁止再按同级 +1 二次合并
        ItemStack existingResult = resultSlots.getItem(0);
        if (!existingResult.isEmpty()) {
            return;
        }
        ItemStack output = leftStack.copy();
        applyRenameIfNeeded(output, leftStack);

        Map<Holder<Enchantment>, Integer> outputEnchants = new HashMap<>();
        ItemEnchantments current = output.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : current.entrySet()) {
            outputEnchants.put(entry.getKey(), entry.getIntValue());
        }
        // 用于判断「合并前后是否无变化」
        Map<Holder<Enchantment>, Integer> beforeEnchants = new HashMap<>(outputEnchants);

        boolean anyChange = false;
        for (Map.Entry<Holder<Enchantment>, Integer> bookEntry : bookEnchants.entrySet()) {
            Holder<Enchantment> ench = bookEntry.getKey();
            int bookLevel = bookEntry.getValue();
            if (ench == null || bookLevel <= 0) {
                continue;
            }
            int currentLevel = outputEnchants.getOrDefault(ench, 0);
            int mergedLevel = (currentLevel == bookLevel) ? currentLevel + 1 : Math.max(currentLevel, bookLevel);
            int cappedLevel = Math.min(mergedLevel, ench.value().getMaxLevel());
            // 不降级：特殊物品（如资源增幅镐内置 LV2）保留高于 max_level 的现有等级
            int newLevel = Math.max(currentLevel, cappedLevel);
            if (newLevel != currentLevel) {
                outputEnchants.put(ench, newLevel);
                anyChange = true;
            }
        }

        if (!anyChange) {
            // 无有效附魔合并：保留原版结果（可能仅有改名），不覆盖、不制造空吞书
            return;
        }

        boolean hasAnyValid = false;
        for (Map.Entry<Holder<Enchantment>, Integer> e : outputEnchants.entrySet()) {
            if (e.getValue() > 0) {
                hasAnyValid = true;
                break;
            }
        }
        if (!hasAnyValid) {
            return;
        }

        // 与合并前完全一致时也不再写入（防御）
        if (beforeEnchants.equals(outputEnchants) && existingResult.isEmpty()
                && enchantmentsEqual(leftStack, outputEnchants)) {
            return;
        }

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Map.Entry<Holder<Enchantment>, Integer> e : outputEnchants.entrySet()) {
            if (e.getValue() > 0) {
                mutable.set(e.getKey(), e.getValue());
            }
        }
        output.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        resultSlots.setItem(0, output);
    }

    /** 从左槽起建结果时，把铁砧改名框的名称写到结果上，避免吞名 */
    private void applyRenameIfNeeded(ItemStack output, ItemStack leftStack) {
        String name = this.itemName;
        if (name == null || name.isEmpty()) {
            return;
        }
        String current = leftStack.getHoverName().getString();
        if (name.equals(current)) {
            return;
        }
        if (".".equals(name) || " ".equals(name)) {
            return;
        }
        output.set(DataComponents.CUSTOM_NAME, Component.literal(name));
    }

    private static boolean enchantmentsEqual(ItemStack stack, Map<Holder<Enchantment>, Integer> map) {
        ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        Map<Holder<Enchantment>, Integer> stackMap = new HashMap<>();
        for (var entry : ench.entrySet()) {
            if (entry.getIntValue() > 0) {
                stackMap.put(entry.getKey(), entry.getIntValue());
            }
        }
        return stackMap.equals(map);
    }

    /**
     * H1：结果槽非空则经验固定 1 级（设计选择，不做限制，见 DESIGN §6.2）。
     */
    private void applyFixedExperienceCostForcefully() {
        ResultContainer resultSlots = getResultSlots();
        if (resultSlots != null && !resultSlots.getItem(0).isEmpty()) {
            this.cost.set(1);
        }
    }

    private static Map<Holder<Enchantment>, Integer> readStoredEnchantments(ItemStack bookStack) {
        Map<Holder<Enchantment>, Integer> result = new HashMap<>();
        if (bookStack == null || bookStack.isEmpty() || bookStack.getItem() != Items.ENCHANTED_BOOK) {
            return result;
        }
        ItemEnchantments stored = bookStack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : stored.entrySet()) {
            if (entry.getIntValue() > 0) {
                result.merge(entry.getKey(), entry.getIntValue(), Math::max);
            }
        }
        return result;
    }
}
