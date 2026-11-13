package com.wenxing.wenxingtools.menu;

import com.wenxing.wenxingtools.item.ModBlocks;
import com.wenxing.wenxingtools.item.WenxingChestplateItem;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * wenxing铁砧的界面：继承原版 {@link AnvilMenu}，规则全部由 {@link #createResult()} 自建。
 *
 * <p>不调 {@code super.createResult()}：原版那 200 行同时承载「过贵 40 清空 / 材料计数 /
 * 附魔可否判定 / 成本重算」，与本方块「任意附魔、代价固定 1 点经验」的语义冲突。
 *
 * <p>代价单位是经验点（{@link WenxingAnvilRules#COST_XP_POINTS}），不是经验等级：
 * 原版 {@code onTake} 扣的是 {@code giveExperienceLevels(-getCost())}，故本类覆盖
 * {@code onTake} 自行扣点，并把 {@code cost} 数据槽只当作界面显示位。
 *
 * <p>1.21.1 移植要点（与 1.20.1 源实现的差异只在这些平台细节上）：
 * <ul>
 *   <li>附魔不再用 {@code Map<Enchantment,Integer>} 承载，改走 {@code ItemEnchantments} 组件 +
 *       {@code Holder<Enchantment>}；{@code EnchantmentHelper.getEnchantmentsForCrafting} 已经按
 *       「附魔书读 STORED_ENCHANTMENTS、其它读 ENCHANTMENTS」分派，故不再需要手写读书记录的解析。</li>
 *   <li>原版 {@code cost} / {@code itemName} 是 private，本端未启用 Access Transformer：
 *       成本用 NeoForge 的 {@code setMaximumCost(long)} 写入（它就是 set cost），
 *       名字由本类自己持有（{@link #wenxing$itemName}），命名路径必经覆盖后的
 *       {@link #setItemName(String)}，不依赖原版字段。</li>
 * </ul>
 */
public class WenxingAnvilMenu extends AnvilMenu {

    private static final int INPUT_LEFT = 0;
    private static final int INPUT_RIGHT = 1;

    /** 玩家本次输入的名字；{@code null} 表示本次会话还没输入过。 */
    @Nullable
    private String wenxing$itemName;

    /** setItemName 时的左槽快照：只有它与当前左槽仍是同一件物品，菜单里的名字才归属这件物品。 */
    private ItemStack wenxing$nameOwner = ItemStack.EMPTY;

    public WenxingAnvilMenu(int containerId, Inventory inventory) {
        super(containerId, inventory);
    }

    public WenxingAnvilMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(containerId, inventory, access);
    }

    @Override
    public void createResult() {
        ItemStack output = buildOutput(this.inputSlots.getItem(INPUT_LEFT), this.inputSlots.getItem(INPUT_RIGHT));
        this.resultSlots.setItem(0, output);
        setMaximumCost(WenxingAnvilRules.costFor(!output.isEmpty()));
    }

    private ItemStack buildOutput(ItemStack left, ItemStack right) {
        if (left.isEmpty() || left.getCount() != 1) {
            return ItemStack.EMPTY;
        }

        boolean rightEmpty = right.isEmpty();
        boolean rightIsBook = right.is(Items.ENCHANTED_BOOK);
        boolean rightMergeable = !rightEmpty && right.getItem() == left.getItem()
                && left.isDamageableItem() && right.getCount() == 1;

        ItemEnchantments leftEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(left);
        ItemStack output = left.copy();
        boolean changed = false;

        if (rightMergeable) {
            int mergedDamage = WenxingAnvilRules.mergedDamage(
                    left.getDamageValue(), right.getDamageValue(), left.getMaxDamage());
            changed |= mergedDamage != left.getDamageValue();
            output.setDamageValue(mergedDamage);
            ItemEnchantments merged = mergeEnchantments(leftEnchantments,
                    EnchantmentHelper.getEnchantmentsForCrafting(right));
            EnchantmentHelper.setEnchantments(output, merged);
            changed |= enchantmentsDiffer(merged, leftEnchantments);
        } else if (rightIsBook) {
            ItemEnchantments merged = mergeEnchantments(leftEnchantments,
                    EnchantmentHelper.getEnchantmentsForCrafting(right));
            EnchantmentHelper.setEnchantments(output, merged);
            changed |= enchantmentsDiffer(merged, leftEnchantments);
            changed |= repairFully(output);
        } else if (rightEmpty) {
            changed |= repairFully(output);
        }

        if (applyItemName(output, left)) {
            changed = true;
        }

        if (!WenxingAnvilRules.shouldProduce(true, rightEmpty, rightIsBook, rightMergeable, changed)) {
            return ItemStack.EMPTY;
        }
        stripCursesIfWenxingChestplate(output);
        return output;
    }

    /**
     * 文星胸甲禁止诅咒附魔（本端设计 §6.4）：结果槽即时剥离 {@code #minecraft:curse}。
     * <p>
     * 这条原来是原版铁砧收尾 Mixin 的职责，改写下线后随结果生成迁到这里；胸甲自身还留有
     * 入包后每刻兜底剥离，覆盖附魔台 / 指令 / 第三方写入等其它途径。
     */
    private static void stripCursesIfWenxingChestplate(ItemStack output) {
        if (output.getItem() instanceof WenxingChestplateItem) {
            WenxingChestplateItem.stripCurseEnchantments(output);
        }
    }

    /** 修复不需要任何材料：右槽空（或右槽是附魔书的附魔路径）就把损伤清零。 */
    private static boolean repairFully(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getDamageValue() == 0) {
            return false;
        }
        stack.setDamageValue(0);
        return true;
    }

    /**
     * 把菜单里的名字写到输出上。
     *
     * <p>名字挂在菜单对象上、不随左槽物品变化，故先用快照确认「这个名字属于当前左件」——
     * 否则玩家给 A 改过名后换 B，B 会被改名并白扣一次代价。
     */
    private boolean applyItemName(ItemStack output, ItemStack left) {
        String customName = this.wenxing$itemName;
        boolean boundToLeft = !this.wenxing$nameOwner.isEmpty()
                && ItemStack.isSameItemSameComponents(left, this.wenxing$nameOwner);
        boolean blank = customName == null || customName.isBlank();
        boolean equalsCurrent = customName != null && customName.equals(left.getHoverName().getString());
        if (!WenxingAnvilRules.shouldApplyName(boundToLeft, blank, equalsCurrent)) {
            return false;
        }
        output.set(DataComponents.CUSTOM_NAME, Component.literal(customName));
        return true;
    }

    private static ItemEnchantments mergeEnchantments(ItemEnchantments base, ItemEnchantments source) {
        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(base);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : source.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int sourceLevel = entry.getIntValue();
            if (enchantment == null || sourceLevel <= 0) {
                continue;
            }
            int currentLevel = merged.getLevel(enchantment);
            int newLevel = WenxingAnvilRules.mergeLevel(currentLevel, sourceLevel,
                    enchantment.value().getMaxLevel());
            if (newLevel > 0) {
                merged.set(enchantment, newLevel);
            }
        }
        return merged.toImmutable();
    }

    /** 逐条比对附魔是否真的变了；不依赖 {@code ItemEnchantments.equals} 的实现细节。 */
    private static boolean enchantmentsDiffer(ItemEnchantments a, ItemEnchantments b) {
        int count = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : a.entrySet()) {
            if (b.getLevel(entry.getKey()) != entry.getIntValue()) {
                return true;
            }
            count++;
        }
        int otherCount = 0;
        for (Holder<Enchantment> ignored : b.keySet()) {
            otherCount++;
        }
        return count != otherCount;
    }

    /**
     * 覆盖原版命名入口：只记录「名字 + 左槽快照」并重算结果，不写原版 private 的 itemName 字段。
     * <p>
     * 判定与原版同构：{@code filterText} 过滤、超长拒绝、与上次同名返回 false（客户端据此决定是否发包），
     * 客户端与服务端都会走到这里（客户端由 {@code AnvilScreen} 的名字输入框调用）。
     */
    @Override
    public boolean setItemName(String name) {
        String filtered = StringUtil.filterText(name == null ? "" : name);
        if (!WenxingAnvilRules.isNameAcceptable(filtered) || filtered.equals(this.wenxing$itemName)) {
            return false;
        }
        this.wenxing$nameOwner = this.inputSlots.getItem(INPUT_LEFT).copy();
        this.wenxing$itemName = filtered;
        this.createResult();
        return true;
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasItem) {
        return hasItem && (player.isCreative() || player.totalExperience >= WenxingAnvilRules.COST_XP_POINTS);
    }

    @Override
    protected void onTake(Player player, ItemStack stack) {
        if (!player.isCreative()) {
            player.giveExperiencePoints(-WenxingAnvilRules.COST_XP_POINTS);
        }
        this.inputSlots.setItem(INPUT_LEFT, ItemStack.EMPTY);
        this.inputSlots.setItem(INPUT_RIGHT, ItemStack.EMPTY);
        setMaximumCost(0);
        this.access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.ANVIL_USE,
                SoundSource.BLOCKS, 1.0F, 1.0F));
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.WENXING_ANVIL.get());
    }
}
