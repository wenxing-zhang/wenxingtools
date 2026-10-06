package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.capability.AuthorityDataProvider;
import com.wenxing.wenxingtools.capability.IAuthorityData;
import com.wenxing.wenxingtools.util.PermissionUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 无敌防御侧「无法锁定」：AI 扫描/锁定目标时跳过无敌玩家。
 * 复仇目标（HurtByTargetGoal 直接 setTarget）不走本判定，仇恨仍可保留。
 */
@Mixin(TargetingConditions.class)
public abstract class TargetingConditionsMixin {

    @Inject(
            method = "test(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void wenxingtools$skipInvincibleLock(LivingEntity attacker, LivingEntity target,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!(target instanceof ServerPlayer player)) {
            return;
        }
        IAuthorityData data = AuthorityDataProvider.getData(player);
        if (data == null) {
            return;
        }
        if (PermissionUtil.isInvincibleCharacteristicActiveOrRevoke(player, data, PermissionUtil.hasPermission(player))) {
            cir.setReturnValue(false);
        }
    }
}
