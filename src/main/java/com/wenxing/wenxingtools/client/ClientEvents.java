package com.wenxing.wenxingtools.client;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.entity.ModEntities;
import com.wenxing.wenxingtools.network.PacketHandler;
import com.wenxing.wenxingtools.network.packet.KeyPacket;
import com.wenxing.wenxingtools.network.packet.ResourceAmpTogglePacket;
import com.wenxing.wenxingtools.util.ModEnums.CharacteristicKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ClientEvents {
    @Mod.EventBusSubscriber(modid = WenXingTools.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModBusEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(KeyInit.KEY_INVINCIBLE);
            event.register(KeyInit.KEY_RESOURCE);
            event.register(KeyInit.KEY_FREEDOM);
            event.register(KeyInit.KEY_KILL_AURA);
            event.register(KeyInit.KEY_RESOURCE_AMPLIFICATION);
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.MULTI_STACK_ITEM.get(), ItemEntityRenderer::new);
        }

    }

    @Mod.EventBusSubscriber(modid = WenXingTools.MODID, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            sendIfClicked(KeyInit.KEY_INVINCIBLE, CharacteristicKey.INVINCIBLE.networkId());
            sendIfClicked(KeyInit.KEY_RESOURCE, CharacteristicKey.RESOURCE.networkId());
            sendIfClicked(KeyInit.KEY_FREEDOM, CharacteristicKey.FREEDOM.networkId());
            sendIfClicked(KeyInit.KEY_KILL_AURA, CharacteristicKey.KILL_AURA.networkId());
            if (KeyInit.KEY_RESOURCE_AMPLIFICATION.consumeClick()) {
                PacketHandler.INSTANCE.sendToServer(new ResourceAmpTogglePacket());
            }
        }

        private static void sendIfClicked(KeyMapping key, int networkId) {
            if (key.consumeClick()) {
                PacketHandler.INSTANCE.sendToServer(new KeyPacket(networkId));
            }
        }
    }
}
