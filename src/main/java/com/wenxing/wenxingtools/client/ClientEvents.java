package com.wenxing.wenxingtools.client;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.entity.ModEntities;
import com.wenxing.wenxingtools.network.ModNetwork;
import com.wenxing.wenxingtools.network.packet.AuthorityDataSyncPacket;
import com.wenxing.wenxingtools.util.ModEnums.CharacteristicKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public final class ClientEvents {
    private ClientEvents() {}

    @EventBusSubscriber(modid = WenXingTools.MODID, value = Dist.CLIENT)
    public static class ClientModBusEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> AuthorityDataSyncPacket.setClientSyncHandler(
                    ClientSyncHandler::handleAuthorityDataSync));
        }

        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(KeyInit.KEY_INVINCIBLE);
            event.register(KeyInit.KEY_RESOURCE);
            event.register(KeyInit.KEY_RESOURCE_AMP);
            event.register(KeyInit.KEY_FREEDOM);
            event.register(KeyInit.KEY_KILL_AURA);
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.MULTI_STACK_ITEM.get(), ItemEntityRenderer::new);
        }
    }

    @EventBusSubscriber(modid = WenXingTools.MODID, value = Dist.CLIENT)
    public static class ClientGameBusEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            sendIfClicked(KeyInit.KEY_INVINCIBLE, CharacteristicKey.INVINCIBLE.networkId());
            sendIfClicked(KeyInit.KEY_RESOURCE, CharacteristicKey.RESOURCE.networkId());
            sendIfClicked(KeyInit.KEY_RESOURCE_AMP, CharacteristicKey.RESOURCE_AMP.networkId());
            sendIfClicked(KeyInit.KEY_FREEDOM, CharacteristicKey.FREEDOM.networkId());
            sendIfClicked(KeyInit.KEY_KILL_AURA, CharacteristicKey.KILL_AURA.networkId());
        }

        private static void sendIfClicked(KeyMapping key, int networkId) {
            if (key.consumeClick()) {
                ModNetwork.sendKeyToServer(networkId);
            }
        }
    }
}
