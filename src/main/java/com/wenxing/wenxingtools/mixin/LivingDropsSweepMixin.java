package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.event.ResourceAmplificationHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;

/**
 * 补扫「事件之后」追加的击杀掉落。
 * <p>
 * 1.21.1 的掉落顺序是：{@code captureDrops(开启)} → 战利品表 / 硬编码 / 装备 → {@code captureDrops(关窗)}
 * → {@code CommonHooks.onLivingDrops} → 集合逐个 {@code addFreshEntity}。注意关窗早于事件：挂在关窗上
 * 拿到的只是事件前的集合，既看不到追加物，也会与主流程重复合并。
 * <p>
 * 本模组的死亡掉落监听器已是 {@code EventPriority.LOWEST}，仍可能早于「同为 LOWEST 但注册更晚」的
 * 监听器；因此必须挂在 {@code CommonHooks.onLivingDrops} 调用之后——那一刻集合已含全部追加，且尚未入世。
 * <p>
 * 取消结果同样传给补扫入口：取消时不放大，但把线程内上下文一并清掉，避免残留值影响下一次死亡。
 * {@code CommonHooks} 是 NeoForge 新增类，不参与映射重命名，故 {@code remap = false}。
 */
@Mixin(LivingEntity.class)
public abstract class LivingDropsSweepMixin {

    @Redirect(
            method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/common/CommonHooks;onLivingDrops(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;Ljava/util/Collection;Z)Z",
                    remap = false))
    private boolean wenxingtools$sweepLateDrops(LivingEntity self, DamageSource source,
                                               Collection<ItemEntity> drops, boolean recentlyHit) {
        boolean canceled = CommonHooks.onLivingDrops(self, source, drops, recentlyHit);
        ResourceAmplificationHandler.sweepLateDrops(self, drops, canceled);
        return canceled;
    }
}
