package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.event.ResourceAmplificationEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;

/**
 * 补扫「事件之后」追加的击杀掉落。
 * <p>
 * 原版 {@code dropAllDeathLoot} 的顺序是：{@code captureDrops(开启)} → 战利品表 / 硬编码 / 装备
 * → {@code captureDrops(关窗)} → {@code ForgeHooks.onLivingDrops} → 集合逐个 {@code addFreshEntity}。
 * 注意关窗早于事件：挂在关窗上拿到的只是事件前的集合，既看不到追加物，也会与主流程重复合并。
 * 本模组的事件监听器已是 {@code EventPriority.LOWEST}，仍可能早于「同为 LOWEST 但注册更晚」的监听器；
 * 因此必须挂在 {@code onLivingDrops} 调用之后——那一刻集合已含全部追加，且尚未入世。
 * <p>
 * {@code ForgeHooks} 是 Forge 新增类，不在 SRG 映射表内，故 {@code remap = false}。
 */
@Mixin(LivingEntity.class)
public abstract class LivingDropsSweepMixin {

    @Redirect(
            method = "dropAllDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/common/ForgeHooks;onLivingDrops(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;Ljava/util/Collection;IZ)Z",
                    remap = false),
            require = 1)
    private boolean wenxingtools$sweepLateDrops(LivingEntity self, DamageSource source,
                                               Collection<ItemEntity> drops, int lootingLevel,
                                               boolean recentlyHit) {
        boolean canceled = ForgeHooks.onLivingDrops(self, source, drops, lootingLevel, recentlyHit);
        if (!canceled) {
            ResourceAmplificationEvents.sweepLateDrops(self, drops);
        }
        return canceled;
    }
}
