package com.wenxing.wenxingtools.integration;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.enchantment.EnchantUtil;
import com.wenxing.wenxingtools.enchantment.ModEnchantments;
import com.wenxing.wenxingtools.event.ResourceAmplificationHandler;
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
    private static volatile java.lang.invoke.MethodHandle BULLET_OWNER_HANDLE;
    private static volatile boolean BULLET_OWNER_RESOLVED;
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

    /**
     * 对外暴露的子弹判定：击杀归属解析需要它把「枪械子弹」认成玩家攻击来源。
     * 子弹不是 {@code Projectile} 子类之外的形态也走同一判据，故不合并到 {@code instanceof Projectile} 分支里。
     */
    public static boolean isTaczBulletEntity(@Nullable Entity entity) {
        return isTaczBullet(entity);
    }

    /**
     * 取枪械子弹的持有者：子弹是 {@code Projectile} 时直接取 owner，否则反射调用 {@code getOwner()}。
     * <p>
     * 不能假定 TACZ 子弹一定继承 {@code Projectile}——该实体的父类在版本间变过，一旦不继承，
     * 只认 {@code instanceof Projectile} 的归属解析会让枪械击杀的掉落增幅 / 掠蛋整段失效。
     * 反射失败静默返回 null（软依赖缺失或签名变化时的降级路径）。
     */
    public static LivingEntity getBulletOwner(@Nullable Entity entity) {
        if (!isTaczBullet(entity)) {
            return null;
        }
        if (entity instanceof Projectile projectile) {
            return projectile.getOwner() instanceof LivingEntity owner ? owner : null;
        }
        java.lang.invoke.MethodHandle handle = bulletOwnerHandle();
        if (handle == null) {
            return null;
        }
        try {
            Object owner = handle.invoke(entity);
            return owner instanceof LivingEntity living ? living : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static java.lang.invoke.MethodHandle bulletOwnerHandle() {
        if (BULLET_OWNER_RESOLVED) {
            return BULLET_OWNER_HANDLE;
        }
        synchronized (TACZ_INIT_LOCK) {
            if (BULLET_OWNER_RESOLVED) {
                return BULLET_OWNER_HANDLE;
            }
            try {
                java.lang.reflect.Method method = bulletClass.getMethod("getOwner");
                method.setAccessible(true);
                BULLET_OWNER_HANDLE = java.lang.invoke.MethodHandles.lookup().unreflect(method);
            } catch (Throwable t) {
                BULLET_OWNER_HANDLE = null;
            }
            BULLET_OWNER_RESOLVED = true;
            return BULLET_OWNER_HANDLE;
        }
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
        // 开关在枪物品组件上：关着就不写等级，子弹落地后的倍率自然为 0
        if (!ResourceAmplificationHandler.isResourceAmpEnabled(gunStack)) {
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
