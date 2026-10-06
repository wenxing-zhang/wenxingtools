package com.wenxing.wenxingtools;

import com.wenxing.wenxingtools.block.ModBlocks;
import com.wenxing.wenxingtools.block.entity.ModBlockEntities;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.entity.ModEntities;
import com.wenxing.wenxingtools.event.CharacteristicTickHandler;
import com.wenxing.wenxingtools.event.CombatCharacteristicHandlers;
import com.wenxing.wenxingtools.event.ModEvents;
import com.wenxing.wenxingtools.event.WorldTickHandlers;
import com.wenxing.wenxingtools.item.ModCreativeTab;
import com.wenxing.wenxingtools.item.ModItems;
import com.wenxing.wenxingtools.network.PacketHandler;
import com.wenxing.wenxingtools.util.WhitelistManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(WenXingTools.MODID)
public class WenXingTools {
    public static final String MODID = "wenxingtools";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WenXingTools() {
        FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();
        IEventBus modEventBus = context.getModEventBus();
        ModEnchantments.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTab.register(modEventBus);
        ModEntities.register(modEventBus);



        modEventBus.addListener(this::commonSetup);


        MinecraftForge.EVENT_BUS.register(ModEvents.ForgeEvents.class);
        MinecraftForge.EVENT_BUS.register(CombatCharacteristicHandlers.class);
        MinecraftForge.EVENT_BUS.register(CharacteristicTickHandler.class);
        MinecraftForge.EVENT_BUS.register(WorldTickHandlers.class);
        MinecraftForge.EVENT_BUS.register(com.wenxing.wenxingtools.event.CompatGameplayEvents.class);

    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PacketHandler.register();
            WhitelistManager.load();
            com.wenxing.wenxingtools.integration.MalumSpiritIntegration.register();
            com.wenxing.wenxingtools.integration.GoetySoulEnergyIntegration.register();
        });
    }
}
