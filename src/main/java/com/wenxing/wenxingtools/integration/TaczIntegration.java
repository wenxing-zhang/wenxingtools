package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = WenXingTools.MODID)
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
        if (!isTaczPresent() || entity == null) {
            return false;
        }
        return bulletClass.isInstance(entity);
    }

    public static boolean isTaczGunItem(@Nullable ItemStack stack) {
        if (!isTaczPresent() || stack == null || stack.isEmpty()) {
            return false;
        }
        return iGunInterface.isInstance(stack.getItem());
    }


    public static boolean isTaczBulletEntity(@Nullable Entity entity) {
        return isTaczBullet(entity);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        Entity entity = event.getEntity();
        if (!isTaczBullet(entity)) {
            return;
        }
        if (!(entity instanceof Projectile projectile)) {
            return;
        }
        Entity owner = projectile.getOwner();
        if (!(owner instanceof LivingEntity shooter)) return;
        if (!(shooter instanceof net.minecraft.world.entity.player.Player)) return;
        ItemStack gunStack = findGunStackFromShooter(shooter);
        if (gunStack.isEmpty()) {
            return;
        }

        int resourceLevel = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.RESOURCE_AMPLIFICATION.get(), gunStack);

        if (resourceLevel > 0) {
            entity.getPersistentData().putInt(RESOURCE_AMPLIFICATION_LEVEL_KEY, resourceLevel);
        }
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
