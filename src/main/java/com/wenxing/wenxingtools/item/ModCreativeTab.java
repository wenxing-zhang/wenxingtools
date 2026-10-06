package com.wenxing.wenxingtools.item;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WenXingTools.MODID);

    public static final RegistryObject<CreativeModeTab> WENXING = TABS.register("wenxing",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wenxingtools"))
                    .icon(() -> new ItemStack(ModItems.RESOURCE_AMPLIFICATION_PICKAXE.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.RESOURCE_AMPLIFICATION_PICKAXE.get().getDefaultInstance());
                        output.accept(ModItems.LIFE_AMPLIFICATION_CHESTPLATE.get().getDefaultInstance());
                        output.accept(ModItems.CUSTOM_SPAWNER.get().getDefaultInstance());
                        output.accept(ModItems.GENERIC_SPAWN_EGG.get().getDefaultInstance());
                        addEnchantedBook(output, ModEnchantments.LIFE_AMPLIFICATION.get());
                        addEnchantedBook(output, ModEnchantments.ATTACK_AMPLIFICATION.get());
                        addEnchantedBook(output, ModEnchantments.RESOURCE_AMPLIFICATION.get());
                        addEnchantedBook(output, ModEnchantments.POTION_AMPLIFICATION.get());
                        addEnchantedBook(output, ModEnchantments.SPAWN_EGG_HARVEST.get());
                        addEnchantedBook(output, ModEnchantments.EFFECT_PURGE.get());
                        addEnchantedBook(output, ModEnchantments.ECHO_AMPLIFICATION.get());
                    })
                    .build());

    private ModCreativeTab() {
    }


    private static void addEnchantedBook(CreativeModeTab.Output output, Enchantment enchantment) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(enchantment, enchantment.getMaxLevel()));
        output.accept(book);
    }

    public static void register(IEventBus eventBus) {
        TABS.register(eventBus);
    }
}
