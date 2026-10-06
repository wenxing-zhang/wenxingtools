package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.attachment.AuthorityAttachments;
import com.wenxing.wenxingtools.attachment.AuthorityDataStorage;
import com.wenxing.wenxingtools.attachment.IAuthorityData;
import com.wenxing.wenxingtools.network.ModNetwork;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = WenXingTools.MODID)
public final class ModEvents {
    private ModEvents() {}

    private static final String AUTHORITY_DATA_PERSISTENT_KEY = WenXingTools.MODID + ":authority_data";

    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        // Authority 附件已通过 AttachmentType.copyOnDeath() 自动复制
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.syncAuthorityData(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.level().isClientSide) {
            ModNetwork.syncAuthorityData(player);
        }
        AmplificationSyncCoordinator.reinitializePlayerAmplifications(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CharacteristicTickHandler.clearPlayerState(player.getUUID());
        PotionAmplificationHandler.clearPotionAmplificationPlayerState(player.getUUID());
        LifeAmplificationHandler.clearLifeAmplificationLevelState(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.level().isClientSide) {
            ModNetwork.syncAuthorityData(player);
            CharacteristicTickHandler.reapplyFreedomAfterWorldChange(player);
        }
        AmplificationSyncCoordinator.reinitializePlayerAmplifications(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.level().isClientSide) {
            ModNetwork.syncAuthorityData(player);
            CharacteristicTickHandler.reapplyFreedomAfterWorldChange(player);
        }
        AmplificationSyncCoordinator.reinitializePlayerAmplifications(player);
    }

    @SubscribeEvent
    public static void onPlayerSave(PlayerEvent.SaveToFile event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        IAuthorityData data = AuthorityAttachments.getData(player);
        if (data == null) {
            return;
        }
        CompoundTag tag = data.serializeNBT();
        player.getPersistentData().put(AUTHORITY_DATA_PERSISTENT_KEY, tag);
        AuthorityDataStorage.saveBackup(player);
    }

    /**
     * 登录时特性开关一律重置为关闭（安全默认）。
     * 备份中保留的是倍率/模式/锁定血量等参数，boolean 开关不跨会话自动开启。
     * NeoForge Attachment 为权威源；镜像/外部备份仅在附件数据缺失时兜底，禁止覆盖已加载附件。
     */
    @SubscribeEvent
    public static void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        IAuthorityData data = AuthorityAttachments.getData(player);
        if (data == null) {
            return;
        }
        if (!hasMeaningfulAuthorityPayload(data)) {
            CompoundTag tag = player.getPersistentData().getCompound(AUTHORITY_DATA_PERSISTENT_KEY);
            if (tag != null && !tag.isEmpty()) {
                data.deserializeNBT(tag);
            } else {
                AuthorityDataStorage.loadBackupIfMissing(player, false);
            }
        }

        data.setInvincibleCharacteristic(false);
        data.setResourceCharacteristic(false);
        data.setFreedomCharacteristic(false);
        data.setKillAuraCharacteristic(false);
        data.clearDirty();
    }

    private static boolean hasMeaningfulAuthorityPayload(IAuthorityData data) {
        return data.getLockedHealth() != 20.0f
                || data.getResourceMultiplier() != 10
                || data.getKillAuraMode() != 0
                || data.isInvincibleCharacteristicOn()
                || data.isResourceCharacteristicOn()
                || data.isFreedomCharacteristicOn()
                || data.isKillAuraCharacteristicOn();
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onItemToss(ItemTossEvent event) {
        if (event.isCanceled()) {
            return;
        }
        Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        IAuthorityData data = AuthorityAttachments.getData(sp);
        if (data == null) {
            return;
        }
        if (!PermissionUtil.isResourceCharacteristicActiveOrRevoke(sp, data, PermissionUtil.hasPermission(sp))) {
            return;
        }
        int itemMultiplier = data.getResourceMultiplier();
        if (itemMultiplier <= 0) {
            return;
        }

        ItemEntity itemEntity = event.getEntity();
        ItemStack stack = itemEntity.getItem();
        int originalCount = stack.getCount();

        long finalCount = (long) originalCount * (long) itemMultiplier;
        if (finalCount <= 0) {
            return;
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
        multi.setThrower(player);
        itemEntity.setItem(ItemStack.EMPTY);
        itemEntity.level().addFreshEntity(multi);
        // 取消原 toss：空 ItemEntity 不再入世
        event.setCanceled(true);
    }
}
