package com.wenxing.wenxingtools.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** wenxing铁砧的物品形态：只多一行蓝色说明，行为完全交给 {@link BlockItem}。 */
public class WenxingAnvilItem extends BlockItem {

    public WenxingAnvilItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.wenxingtools.wenxing_anvil.desc")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
