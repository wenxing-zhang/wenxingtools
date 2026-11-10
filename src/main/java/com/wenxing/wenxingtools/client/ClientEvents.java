package com.wenxing.wenxingtools.client;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.entity.ModEntities;
import com.wenxing.wenxingtools.event.AmpSupport;
import com.wenxing.wenxingtools.menu.ModMenuTypes;
import com.wenxing.wenxingtools.network.PacketHandler;
import com.wenxing.wenxingtools.network.packet.KeyPacket;
import com.wenxing.wenxingtools.network.packet.ResourceAmpTogglePacket;
import com.wenxing.wenxingtools.util.ModEnums.CharacteristicKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

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
        public static void onClientSetup(FMLClientSetupEvent event) {
            // 复用原版铁砧界面：MenuType 的类型参数是 AnvilMenu，故这里的 ScreenConstructor 类型匹配
            event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.WENXING_ANVIL.get(), AnvilScreen::new));
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

        /**
         * 手持已开启的物品时每 2 tick 冒一个紫色粒子。
         * <p>
         * 放在客户端：开关写在物品 NBT 上，ItemStack 本身随装备同步，不需要额外网络包，
         * 也不会给服务端 tick 增加负担。仅本机可见——不为其他玩家或女仆渲染。
         */
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
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
            if (!AmpSupport.isResourceAmpEnabled(stack)) {
                return;
            }
            if (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.RESOURCE_AMPLIFICATION.get(), stack) <= 0) {
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
                PacketHandler.INSTANCE.sendToServer(new KeyPacket(networkId));
            }
        }
    }
}
