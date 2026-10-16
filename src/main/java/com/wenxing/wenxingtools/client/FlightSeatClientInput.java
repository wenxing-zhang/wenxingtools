package com.wenxing.wenxingtools.client;

import com.wenxing.wenxingtools.WenXingTools;
import com.wenxing.wenxingtools.entity.FlightSeatEntity;
import com.wenxing.wenxingtools.network.packet.FlightInputPacket;
import com.wenxing.wenxingtools.network.packet.FlightSummonPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 飞行座的客户端输入采集：双击跳跃开关飞行座，骑乘期间每刻上报前 / 升降。
 * 双击用 input.jumping 的上升沿自检，不消费原版跳跃队列，避免与跳跃抢输入。
 */
@EventBusSubscriber(modid = WenXingTools.MODID, value = Dist.CLIENT)
public final class FlightSeatClientInput {
    private FlightSeatClientInput() {}

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

        if (player.getVehicle() instanceof FlightSeatEntity) {
            int forward = (int) Math.signum(player.input.forwardImpulse);
            int up = player.input.jumping ? 1 : (mc.options.keyShift.isDown() ? -1 : 0);
            PacketDistributor.sendToServer(new FlightInputPacket(forward, up));
        }
    }

    private static void detectDoubleJump(LocalPlayer player) {
        boolean jumpDown = player.input.jumping;
        if (jumpDown && !lastJumpDown && toggleGuard == 0) {
            int now = player.tickCount;
            if (now - lastJumpTick <= DOUBLE_TAP_TICKS) {
                lastJumpTick = -100;
                toggleGuard = TOGGLE_GUARD_TICKS;
                PacketDistributor.sendToServer(new FlightSummonPacket());
            } else {
                lastJumpTick = now;
            }
        }
        lastJumpDown = jumpDown;
    }
}
