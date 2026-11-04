package com.wenxing.wenxingtools.client;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.entity.ModEntities;
import com.wenxing.wenxingtools.event.ResourceAmplificationHandler;
import com.wenxing.wenxingtools.network.ModNetwork;
import com.wenxing.wenxingtools.network.packet.AuthorityDataSyncPacket;
import com.wenxing.wenxingtools.util.ModEnums.CharacteristicKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
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

        /**
         * 手持已开启的物品时每 2 tick 冒一个紫色粒子。
         * <p>
         * 放在客户端：开关写在物品组件上且组件随装备同步，不需要额外网络包，也不给服务端 tick 增负。
         * 仅本机可见——不为其他玩家或女仆渲染。
         */
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.level() == null || player.isSpectator()) {
                return;
            }
            if ((player.tickCount & 1) != 0) {
                return;
            }
            spawnResourceAmpParticle(player, player.getMainHandItem());
            spawnResourceAmpParticle(player, player.getOffhandItem());
        }

        private static void spawnResourceAmpParticle(LocalPlayer player, ItemStack stack) {
            if (!ResourceAmplificationHandler.isResourceAmpEnabled(stack)) {
                return;
            }
            if (EnchantUtil.getLevel(ModEnchantments.RESOURCE_AMPLIFICATION, stack, player) <= 0) {
                return;
            }
            Level level = player.level();
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
            double radius = 0.35 + player.getRandom().nextDouble() * 0.2;
            level.addParticle(
                    ParticleTypes.WITCH,
                    player.getX() + Math.cos(angle) * radius,
                    player.getY() + 0.9 + player.getRandom().nextDouble() * 0.4,
                    player.getZ() + Math.sin(angle) * radius,
                    0.0, 0.01, 0.0);
        }

        private static void sendIfClicked(KeyMapping key, int networkId) {
            if (key.consumeClick()) {
                ModNetwork.sendKeyToServer(networkId);
            }
        }
    }
}
