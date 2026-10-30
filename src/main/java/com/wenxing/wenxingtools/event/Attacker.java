package com.wenxing.wenxingtools.event;

import com.wenxing.wenxingtools.util.AttackerKind;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 已解析出的攻击者：实体 + 类别。
 * <p>
 * 所有增幅效果都从这里取攻击者，避免每个事件各自判断「是玩家还是生物」而口径漂移（R4/R5）。
 */
public record Attacker(LivingEntity entity, AttackerKind kind) {

    public Player asPlayer() {
        return entity instanceof Player player ? player : null;
    }

    public boolean isRealPlayer() {
        return kind == AttackerKind.REAL_PLAYER;
    }
}
