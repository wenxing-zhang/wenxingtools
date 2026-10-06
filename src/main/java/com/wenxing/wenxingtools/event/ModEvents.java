package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.capability.AuthorityData;
import com.wenxing.wenxingtools.capability.AuthorityDataProvider;
import com.wenxing.wenxingtools.capability.AuthorityDataStorage;
import com.wenxing.wenxingtools.capability.IAuthorityData;
import com.wenxing.wenxingtools.network.PacketHandler;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

public class ModEvents {

    @Mod.EventBusSubscriber(modid = WenXingTools.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEventBusEvents {
        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
            event.register(IAuthorityData.class);
        }
    }



    public static class ForgeEvents {

        @SubscribeEvent
        public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                if (!event.getObject().getCapability(AuthorityDataProvider.AUTHORITY_DATA).isPresent()) {
                    AuthorityDataProvider provider = new AuthorityDataProvider();
                    event.addCapability(new ResourceLocation(WenXingTools.MODID, "properties"), provider);







                }
            }
        }

        @SubscribeEvent
        public static void onPlayerCloned(PlayerEvent.Clone event) {


            Entity original = event.getOriginal();
            com.wenxing.wenxingtools.compat.SoftModCompat.copyPersistentGiftFlags(original, event.getEntity());
            original.reviveCaps();
            try {
                original.getCapability(AuthorityDataProvider.AUTHORITY_DATA).ifPresent(oldStore -> {
                    event.getEntity().getCapability(AuthorityDataProvider.AUTHORITY_DATA).ifPresent(newStore -> {
                        newStore.copyFrom(oldStore);
                    });
                });
            } finally {
                original.invalidateCaps();
            }
            if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ServerPlayer player) {
                PacketHandler.syncAuthorityData(player);
            }
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerLoggedInEvent event) {
            if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ServerPlayer player) {
                PacketHandler.syncAuthorityData(player);
            }


            if (event.getEntity() != null) {
                EnchantmentEvents.reinitializePlayerAmplifications(event.getEntity());
            }
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
            if (event.getEntity() != null) {
                UUID pid = event.getEntity().getUUID();
                CharacteristicTickHandler.clearPlayerState(pid);
                EnchantmentEvents.clearPotionAmplificationPlayerState(pid);
                EnchantmentEvents.clearLifeAmplificationLevelState(pid);
                com.wenxing.wenxingtools.network.packet.KeyPacket.clearCooldown(pid);
                com.wenxing.wenxingtools.network.packet.ResourceAmpTogglePacket.clearCooldown(pid);
                ResourceAmplificationEvents.clearPlayerResourceContexts();
                com.wenxing.wenxingtools.integration.GoetySoulEnergyIntegration.clearFactorContext();
            }
        }

        @SubscribeEvent
        public static void onPlayerRespawn(PlayerRespawnEvent event) {
            if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ServerPlayer player) {
                PacketHandler.syncAuthorityData(player);

                CharacteristicTickHandler.reapplyFreedomAfterWorldChange(player);
            }


            if (event.getEntity() != null) {
                EnchantmentEvents.reinitializePlayerAmplifications(event.getEntity());
            }
        }

        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
            if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ServerPlayer player) {
                PacketHandler.syncAuthorityData(player);


                CharacteristicTickHandler.reapplyFreedomAfterWorldChange(player);
            }

            if (event.getEntity() != null) {
                EnchantmentEvents.reinitializePlayerAmplifications(event.getEntity());
            }
        }

        @SubscribeEvent
        public static void onPlayerSave(PlayerEvent.SaveToFile event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                AuthorityDataStorage.saveBackup(player);
            }
        }

        @SubscribeEvent
        public static void onPlayerLoad(PlayerEvent.LoadFromFile event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                IAuthorityData data = AuthorityDataProvider.getData(player);
                if (data != null) {
                    CompoundTag defaults = new AuthorityData().serializeNBT();
                    CompoundTag current = data.serializeNBT();
                    boolean primaryLoaded = !current.equals(defaults);
                    if (!primaryLoaded) {
                        AuthorityDataStorage.loadBackupIfMissing(player, false);
                    }
                    data.setInvincibleCharacteristic(false);
                    data.setResourceCharacteristic(false);
                    data.setFreedomCharacteristic(false);
                    data.setKillAuraCharacteristic(false);
                    calibrateLockedHealth(player, data);
                }
            }
        }

        private static void calibrateLockedHealth(ServerPlayer player, IAuthorityData data) {
            float locked = data.getLockedHealth();
            float max = player.getMaxHealth();
            if (max <= 0.0f || Float.isNaN(max) || Float.isInfinite(max)) {
                return;
            }
            boolean invalid = Float.isNaN(locked) || Float.isInfinite(locked) || locked <= 0.0f || locked > max;
            if (!invalid) {
                return;
            }
            float fallback = player.getHealth();
            if (Float.isNaN(fallback) || Float.isInfinite(fallback) || fallback <= 0.0f) {
                fallback = max;
            }
            if (fallback > max) {
                fallback = max;
            }
            data.setLockedHealth(fallback);
        }

        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void onItemToss(ItemTossEvent event) {
            if (event.isCanceled()) return;
            Player player = event.getPlayer();
            if (!(player instanceof ServerPlayer sp)) return;
            IAuthorityData data = AuthorityDataProvider.getData(sp);
            if (data == null) return;
            if (!PermissionUtil.isResourceCharacteristicActiveOrRevoke(sp, data, PermissionUtil.hasPermission(sp))) return;
            int itemMultiplier = data.getResourceMultiplier();
            if (itemMultiplier <= 0) return;

            ItemEntity itemEntity = event.getEntity();
            ItemStack stack = itemEntity.getItem();
            int originalCount = stack.getCount();

            long finalCount = (long) originalCount * (long) itemMultiplier;
            if (finalCount <= 0) {
                finalCount = originalCount;
            }
            if (finalCount > Integer.MAX_VALUE) {
                finalCount = Integer.MAX_VALUE;
            }

            ItemStack multiplied = stack.copy();
            multiplied.setCount((int) finalCount);
            com.wenxing.wenxingtools.entity.MultiStackItemEntity multi =
                    com.wenxing.wenxingtools.entity.MultiStackItemEntity.fromStacks(
                            itemEntity.level(),
                            itemEntity.getX(),
                            itemEntity.getY(),
                            itemEntity.getZ(),
                            java.util.List.of(multiplied));
            multi.setDeltaMovement(itemEntity.getDeltaMovement());
            multi.setPickUpDelay(40);
            multi.setThrower(player.getUUID());
            itemEntity.setItem(ItemStack.EMPTY);
            itemEntity.level().addFreshEntity(multi);
            event.setCanceled(true);
        }
    }
}
