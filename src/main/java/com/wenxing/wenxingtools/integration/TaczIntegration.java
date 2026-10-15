package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = WenXingTools.MODID)
public class TaczIntegration {

    private static final String RESOURCE_AMPLIFICATION_LEVEL_KEY = WenXingTools.MODID + ":resource_amplification_level";

    private static final String TACZ_BULLET_CLASS_NAME = "com.tacz.guns.entity.EntityKineticBullet";
    private static final String TACZ_IGUN_INTERFACE_NAME = "com.tacz.guns.api.item.IGun";

    private static volatile Class<?> bulletClass;
    private static volatile Class<?> iGunInterface;
    private static volatile Boolean taczPresent;
    private static final Object TACZ_INIT_LOCK = new Object();

    private static boolean isTaczPresent() {
        Boolean result = taczPresent;
        if (result != null) {
            return result;
        }
        synchronized (TACZ_INIT_LOCK) {
            result = taczPresent;
            if (result != null) {
                return result;
            }
            try {
                ClassLoader cl = TaczIntegration.class.getClassLoader();
                bulletClass = Class.forName(TACZ_BULLET_CLASS_NAME, false, cl);
                iGunInterface = Class.forName(TACZ_IGUN_INTERFACE_NAME, false, cl);
                taczPresent = Boolean.TRUE;
            } catch (Throwable t) {
                taczPresent = Boolean.FALSE;
            }
            return taczPresent;
        }
    }

    private static boolean isTaczBullet(@Nullable Entity entity) {
        return isTaczPresent() && entity != null && bulletClass.isInstance(entity);
    }

    public static boolean isTaczGunItem(@Nullable ItemStack stack) {
        return isTaczPresent() && stack != null && !stack.isEmpty() && iGunInterface.isInstance(stack.getItem());
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        Entity entity = event.getEntity();
        if (!isTaczBullet(entity) || !(entity instanceof Projectile projectile)) {
            return;
        }
        if (tryAssignBulletResourceLevel(entity, projectile)) {
            return;
        }
        // join 时 owner 可能尚未赋值：延后一刻重试一次
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.getServer() != null) {
            serverLevel.getServer().execute(() -> {
                if (!entity.isRemoved() && !entity.level().isClientSide) {
                    tryAssignBulletResourceLevel(entity, projectile);
                }
            });
        }
    }

    /** 写成功（或 owner 已就位 / 无需写入）返回 true；owner 仍为空返回 false 供调用方延后重试。 */
    private static boolean tryAssignBulletResourceLevel(Entity entity, Projectile projectile) {
        Entity owner = projectile.getOwner();
        if (!(owner instanceof net.minecraft.world.entity.player.Player shooter)) {
            return false;
        }
        if (entity.getPersistentData().contains(RESOURCE_AMPLIFICATION_LEVEL_KEY)) {
            return true;
        }
        ItemStack gunStack = findGunStackFromShooter(shooter);
        if (gunStack.isEmpty()) {
            return true;
        }
        int resourceLevel = EnchantUtil.getLevel(ModEnchantments.RESOURCE_AMPLIFICATION, gunStack, shooter);
        if (resourceLevel > 0) {
            entity.getPersistentData().putInt(RESOURCE_AMPLIFICATION_LEVEL_KEY, resourceLevel);
        }
        return true;
    }

    private static ItemStack findGunStackFromShooter(LivingEntity shooter) {
        ItemStack mainHand = shooter.getMainHandItem();
        if (!mainHand.isEmpty() && isTaczGunItem(mainHand)) {
            return mainHand;
        }
        ItemStack offHand = shooter.getOffhandItem();
        if (!offHand.isEmpty() && isTaczGunItem(offHand)) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }

    public static int getResourceAmplificationLevelFromBullet(@Nullable Entity directEntity) {
        if (!isTaczBullet(directEntity)) {
            return 0;
        }
        return directEntity.getPersistentData().getInt(RESOURCE_AMPLIFICATION_LEVEL_KEY);
    }
}
