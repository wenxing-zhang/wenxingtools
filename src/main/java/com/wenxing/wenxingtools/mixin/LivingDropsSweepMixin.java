package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.event.ResourceAmplificationEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;

/**
 * 补扫「事件之后」追加的击杀掉落。
 * <p>
 * 原版 {@code dropAllDeathLoot} 的顺序是：{@code captureDrops(开启)} → 战利品表 / 硬编码 / 装备掉落
 * → {@code captureDrops(关窗)} → {@code LivingDropsEvent} → 集合逐个 {@code addFreshEntity}。
 * 本模组的事件监听器已是 {@code EventPriority.LOWEST}，仍可能早于「同为 LOWEST 但注册更晚」的监听器；
 * 关窗那一刻取到的集合已包含全部追加，晚于任何监听器，故在此补扫。
 * <p>
 * {@code captureDrops} 是 Forge 在原版之后新增到 {@code Entity} 的方法（SRG 表中不存在，运行时保持原名）。
 */
@Mixin(LivingEntity.class)
public abstract class LivingDropsSweepMixin {

    @Redirect(
            method = "dropAllDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;captureDrops(Ljava/util/Collection;)Ljava/util/Collection;"),
            require = 1)
    private Collection<ItemEntity> wenxingtools$sweepLateDrops(LivingEntity self, Collection<ItemEntity> incoming) {
        Collection<ItemEntity> drops = self.captureDrops(incoming);
        // 开启那次传入的是新 ArrayList，关窗那次传 null；只有关窗拿到的才是最终集合
        if (incoming == null && drops != null) {
            ResourceAmplificationEvents.sweepLateDrops(self, drops);
        }
        return drops;
    }
}
