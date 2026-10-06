package com.wenxing.wenxingtools.item;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * 创造栏「文星工具」：资源增幅镐、文星刷怪笼、本模组全部附魔书（满级）。
 * 专属栏独立注册，不再混入原版工具栏。
 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WenXingTools.MODID);

    private static final List<ResourceKey<Enchantment>> BOOK_ENCHANTMENTS = List.of(
            ModEnchantments.LIFE_AMPLIFICATION,
            ModEnchantments.ATTACK_AMPLIFICATION,
            ModEnchantments.RESOURCE_AMPLIFICATION,
            ModEnchantments.POTION_AMPLIFICATION,
            ModEnchantments.SPAWN_EGG_HARVEST,
            ModEnchantments.EFFECT_PURGE,
            ModEnchantments.ECHO_AMPLIFICATION
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WENXING_TOOLS =
            CREATIVE_MODE_TABS.register("wenxing_tools", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wenxingtools.wenxing"))
                    .icon(() -> new ItemStack(ModItems.RESOURCE_AMPLIFICATION_PICKAXE.get()))
                    .displayItems(ModCreativeTabs::buildDisplayItems)
                    .build());

    private static void buildDisplayItems(
            CreativeModeTab.ItemDisplayParameters parameters,
            CreativeModeTab.Output output) {
        output.accept(ModItems.RESOURCE_AMPLIFICATION_PICKAXE.get());
        output.accept(ModItems.CUSTOM_SPAWNER.get());

        var enchantmentLookup = parameters.holders().lookupOrThrow(Registries.ENCHANTMENT);
        for (ResourceKey<Enchantment> key : BOOK_ENCHANTMENTS) {
            enchantmentLookup.get(key).ifPresent(holder -> output.accept(createEnchantedBook(holder)));
        }
    }

    /** 附魔书：写入 STORED_ENCHANTMENTS，等级 = 该附魔 max_level */
    private static ItemStack createEnchantedBook(Holder<Enchantment> holder) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(holder, Math.max(1, holder.value().getMaxLevel()));
        stack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return stack;
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
