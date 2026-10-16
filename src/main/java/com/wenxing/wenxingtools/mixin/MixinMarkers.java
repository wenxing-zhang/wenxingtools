package com.wenxing.wenxingtools.mixin;

/**
 * Mixin 落地标记：每个 Mixin 类实现其中一个空接口，Mixin 成功应用时会把这个接口加到目标类上。
 * <p>
 * 启动自检据此判定「注入是否真的落到目标类」，比「反射查目标成员是否存在」强一层：成员存在但调用点
 * 消失时注入会打空，旧口径检测不到；而任一注入点打空都会让整个 Mixin 应用失败，标记随之缺失。
 * <p>
 * 空接口不含任何成员，对目标类无行为影响；只由 Mixin 类实现，普通类不得实现。
 */
public final class MixinMarkers {
    private MixinMarkers() {
    }

    public interface AnvilAnyEnchantment {
    }

    public interface AnvilResultFinalize {
    }

    public interface EnchantNameDisplay {
    }

    public interface WeaponCoverageExpand {
    }

    public interface PurgeVoidRewrite {
    }

    public interface PurgeEffectWriteBlock {
    }

    public interface FlyMiningIgnore {
    }

    public interface FixedMining {
    }

    public interface MultiStackMergeGuard {
    }

    /** 自由飞行接入原版飞行代码路径（Player.travel / getFlyingSpeed 重定向） */
    public interface FreedomFlightTravel {
    }

    /** 自由飞行客户端垂直输入（LocalPlayer.aiStep 注入） */
    public interface FreedomFlightClient {
    }

    /** 自由飞行悬空检测豁免（ServerGamePacketListenerImpl.tick 注入） */
    public interface FreedomFlightGuard {
    }
}
