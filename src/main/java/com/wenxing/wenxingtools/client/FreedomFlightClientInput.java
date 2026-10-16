package com.wenxing.wenxingtools.client;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.network.packet.FlightTogglePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 自由飞行的客户端输入：双击跳跃切换飞行状态（起飞 / 收起）。
 * 双击用 input.jumping 的上升沿自检，不消费原版跳跃队列，避免与跳跃抢输入。
 * 飞行位移与升降由 Player / LocalPlayer 的原版飞行代码路径负责，本类只上报开关。
 */
@EventBusSubscriber(modid = WenXingTools.MODID, value = Dist.CLIENT)
public final class FreedomFlightClientInput {
    private FreedomFlightClientInput() {}

    private static final int DOUBLE_TAP_TICKS = 7;
    private static final int TOGGLE_GUARD_TICKS = 10;

    private static boolean lastJumpDown = false;
    private static int lastJumpTick = -100;
    private static int toggleGuard = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (toggleGuard > 0) {
            --toggleGuard;
        }
        detectDoubleJump(player);
    }

    /** 断开连接后清掉上升沿与守卫，避免跨会话残留。 */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        lastJumpDown = false;
        lastJumpTick = -100;
        toggleGuard = 0;
    }

    private static void detectDoubleJump(LocalPlayer player) {
        boolean jumpDown = player.input.jumping;
        if (jumpDown && !lastJumpDown && toggleGuard == 0) {
            int now = player.tickCount;
            if (now - lastJumpTick <= DOUBLE_TAP_TICKS) {
                lastJumpTick = -100;
                toggleGuard = TOGGLE_GUARD_TICKS;
                PacketDistributor.sendToServer(new FlightTogglePacket());
            } else {
                lastJumpTick = now;
            }
        }
        lastJumpDown = jumpDown;
    }
}
