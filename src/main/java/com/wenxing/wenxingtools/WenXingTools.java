package com.wenxing.wenxingtools;

import com.wenxing.wenxingtools.attachment.AuthorityAttachments;
import com.wenxing.wenxingtools.entity.ModEntities;
import com.wenxing.wenxingtools.integration.GoetySoulEnergyAmplification;
import com.wenxing.wenxingtools.integration.MalumSpiritAmplification;
import com.wenxing.wenxingtools.item.ModBlocks;
import com.wenxing.wenxingtools.item.ModCreativeTabs;
import com.wenxing.wenxingtools.item.ModItems;
import com.wenxing.wenxingtools.util.MixinSelfCheck;
import com.wenxing.wenxingtools.util.WhitelistManager;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(WenXingTools.MODID)
public class WenXingTools {
    public static final String MODID = "wenxingtools";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WenXingTools(IEventBus modEventBus) {
        AuthorityAttachments.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModEntities.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(MixinSelfCheck::run);
        event.enqueueWork(WhitelistManager::load);
        event.enqueueWork(MalumSpiritAmplification::register);
        event.enqueueWork(GoetySoulEnergyAmplification::register);
        LOGGER.info("[wenxingtools] common setup complete");
    }
}
