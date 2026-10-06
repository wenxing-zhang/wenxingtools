package com.wenxing.wenxingtools.enchantment;

import com.wenxing.wenxingtools.integration.TaczIntegration;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.Nullable;

/**
 * 掠蛋武器覆盖扩展（对齐 Forge 1.20.1 {@code canEnchant = isWeaponOrTool || isTaczGunItem}）。
 * <p>
 * 1.21 附魔适用范围由 {@code supported_items} 标签驱动，无法把「所有 IGun / 模组 SwordItem 子类」
 * 写进静态标签。这里在 {@code isSupportedItem}/{@code canEnchant} 上做动态放行：
 * TACZ 枪 + 武器类子类（含未进原版 {@code enchantable/*} 标签的第三方近战/远程）。
 * 仅针对掠蛋；其余增幅附魔仍走 {@code #wenxingtools:weapon_or_tool} 标签。
 */
public final class ExpandedEnchantCoverage {
    private ExpandedEnchantCoverage() {}

    /** 与 spawn_egg_harvest.json 的 description.translate 一致。 */
    private static final String SPAWN_EGG_HARVEST_DESC_KEY =
            "enchantment.wenxingtools.spawn_egg_harvest";

    public static boolean isSpawnEggHarvest(@Nullable Enchantment enchantment) {
        if (enchantment == null) {
            return false;
        }
        try {
            Component desc = enchantment.description();
            return desc != null
                    && desc.getContents() instanceof TranslatableContents tc
                    && SPAWN_EGG_HARVEST_DESC_KEY.equals(tc.getKey());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 动态武器：TACZ 枪（{@code IGun}）或常见武器类（含模组子类）。
     * 已在 {@code #wenxingtools:weapon_or_tool} 标签内的物品仍以标签为准，这里只负责标签罩不住的。
     */
    public static boolean isExpandedWeapon(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (TaczIntegration.isTaczGunItem(stack)) {
            return true;
        }
        var item = stack.getItem();
        return item instanceof SwordItem
                || item instanceof DiggerItem
                || item instanceof MaceItem
                || item instanceof TridentItem
                || item instanceof ProjectileWeaponItem
                || item instanceof BowItem;
    }

    /** 掠蛋 + 动态武器 → 允许附着。 */
    public static boolean allowsSpawnEggHarvest(@Nullable Enchantment enchantment, @Nullable ItemStack stack) {
        return isSpawnEggHarvest(enchantment) && isExpandedWeapon(stack);
    }
}
