package com.wenxing.wenxingtools.menu;

import com.wenxing.wenxingtools.block.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * wenxing铁砧的界面：继承原版 {@link AnvilMenu}，规则全部由 {@link #createResult()} 自建。
 *
 * <p>不调 {@code super.createResult()}：原版那 200 行同时承载「过贵 40 清空 / 材料计数 /
 * 附魔可否判定 / 成本重算」，与本方块「任意附魔、代价固定 1 点经验」的语义冲突。
 *
 * <p>代价单位是经验点（{@link WenxingAnvilRules#COST_XP_POINTS}），不是经验等级：
 * 原版 {@code onTake} 扣的是 {@code giveExperienceLevels(-getCost())}，故本类覆盖
 * {@code onTake} 自行扣点，并把 {@code cost} 数据槽只当作界面显示位。
 */
public class WenxingAnvilMenu extends AnvilMenu {

    private static final int INPUT_LEFT = 0;
    private static final int INPUT_RIGHT = 1;

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
        this.cost.set(WenxingAnvilRules.costFor(!output.isEmpty()));
    }

    private ItemStack buildOutput(ItemStack left, ItemStack right) {
        if (left.isEmpty() || left.getCount() != 1) {
            return ItemStack.EMPTY;
        }

        boolean rightEmpty = right.isEmpty();
        boolean rightIsBook = right.is(Items.ENCHANTED_BOOK);
        boolean rightMergeable = !rightEmpty && right.getItem() == left.getItem()
                && left.isDamageableItem() && right.getCount() == 1;

        Map<Enchantment, Integer> leftEnchantments = EnchantmentHelper.getEnchantments(left);
        ItemStack output = left.copy();
        boolean changed = false;

        if (rightMergeable) {
            int mergedDamage = WenxingAnvilRules.mergedDamage(
                    left.getDamageValue(), right.getDamageValue(), left.getMaxDamage());
            changed |= mergedDamage != left.getDamageValue();
            output.setDamageValue(mergedDamage);
            Map<Enchantment, Integer> merged =
                    mergeEnchantments(leftEnchantments, EnchantmentHelper.getEnchantments(right));
            EnchantmentHelper.setEnchantments(merged, output);
            changed |= !merged.equals(leftEnchantments);
        } else if (rightIsBook) {
            Map<Enchantment, Integer> merged = mergeEnchantments(leftEnchantments, readStoredEnchantments(right));
            EnchantmentHelper.setEnchantments(merged, output);
            changed |= !merged.equals(leftEnchantments);
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
        return output;
    }

    /** 修复不需要任何材料：右槽空（或右槽是附魔书的附魔路径）就把损伤清零。 */
    private static boolean repairFully(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getDamageValue() == 0) {
            return false;
        }
        stack.setDamageValue(0);
        return true;
    }

    private boolean applyItemName(ItemStack output, ItemStack left) {
        // itemName 由原版 setItemName(String) 写入（Access Transformer 公开的字段）；
        // 与左件当前显示名相同则视为未改名。
        String customName = this.itemName;
        if (customName == null || customName.isBlank()) {
            return false;
        }
        if (customName.equals(left.getHoverName().getString())) {
            return false;
        }
        output.setHoverName(Component.literal(customName));
        return true;
    }

    private static Map<Enchantment, Integer> mergeEnchantments(Map<Enchantment, Integer> base,
                                                              Map<Enchantment, Integer> source) {
        Map<Enchantment, Integer> merged = new HashMap<>(base);
        for (Map.Entry<Enchantment, Integer> entry : source.entrySet()) {
            Enchantment enchantment = entry.getKey();
            Integer sourceLevel = entry.getValue();
            if (enchantment == null || sourceLevel == null || sourceLevel <= 0) {
                continue;
            }
            int currentLevel = merged.getOrDefault(enchantment, 0);
            merged.put(enchantment, WenxingAnvilRules.mergeLevel(currentLevel, sourceLevel, enchantment.getMaxLevel()));
        }
        merged.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0);
        return merged;
    }

    /** 附魔书的附魔存在 {@code StoredEnchantments}，与物品的 {@code Enchantments} 不是同一个标签。 */
    private static Map<Enchantment, Integer> readStoredEnchantments(ItemStack book) {
        Map<Enchantment, Integer> result = new HashMap<>(4);
        CompoundTag tag = book.getTag();
        if (tag == null) {
            return result;
        }
        ListTag stored = tag.getList("StoredEnchantments", 10);
        for (int i = 0; i < stored.size(); i++) {
            CompoundTag entry = stored.getCompound(i);
            ResourceLocation id = EnchantmentHelper.getEnchantmentId(entry);
            if (id == null) {
                continue;
            }
            Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(id);
            if (enchantment == null) {
                continue;
            }
            int level = EnchantmentHelper.getEnchantmentLevel(entry);
            if (level <= 0) {
                continue;
            }
            result.merge(enchantment, level, Math::max);
        }
        return result;
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasItem) {
        return hasItem && (player.isCreative() || player.totalExperience >= WenxingAnvilRules.COST_XP_POINTS);
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        if (!player.isCreative()) {
            player.giveExperiencePoints(-WenxingAnvilRules.COST_XP_POINTS);
        }
        this.inputSlots.setItem(INPUT_LEFT, ItemStack.EMPTY);
        this.inputSlots.setItem(INPUT_RIGHT, ItemStack.EMPTY);
        this.cost.set(0);
        this.access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.ANVIL_USE,
                SoundSource.BLOCKS, 1.0F, 1.0F));
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.WENXING_ANVIL.get());
    }
}
