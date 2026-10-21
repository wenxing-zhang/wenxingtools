package com.wenxing.wenxingtools.mixin;

import com.wenxing.wenxingtools.util.MixinMarkers;
import com.wenxing.wenxingtools.item.WenxingPickaxeItem;
import com.wenxing.wenxingtools.util.MixinSelfCheck;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 主手持有资源增幅镐时，取消原版「不在地面（含飞行）挖掘速度 ÷5」惩罚。
 * 1.21.1 破坏速度入口是 getDigSpeed（getDestroySpeed 转发至其无位置变体）。
 * 实现为原位修正 ÷5 的除数常量（置 1.0F 等效取消），仅在该惩罚实际执行时介入；
 * 不抢占方法内的 onGround() 调用点——该点常被他模组重定向，同点抢占会互斥并致注入失败方启动崩溃。
 * <p>
 * 该方法内 float 5.0F 常量唯一（已对照 1.21.1 反编译源），故不写 ordinal。
 */
@Mixin(Player.class)
public abstract class PlayerDestroySpeedMixin implements MixinMarkers.FlyMiningIgnore {

    @ModifyConstant(
            method = "getDigSpeed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)F",
            constant = @Constant(floatValue = 5.0F)
    )
    private float wenxingtools$ignoreFlyMiningPenalty(float original) {
        if (!MixinSelfCheck.isFlyMiningIgnoreEnabled()) {
            return original;
        }
        Player self = (Player) (Object) this;
        if (self.getMainHandItem().getItem() instanceof WenxingPickaxeItem) {
            return 1.0F;
        }
        return original;
    }
}
