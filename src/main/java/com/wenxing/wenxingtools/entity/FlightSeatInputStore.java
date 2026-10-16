package com.wenxing.wenxingtools.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 飞行座的玩家输入暂存：客户端每刻上报，服务端在实体 tick 时读取。
 * 只在服务端主线程访问——网络包已 enqueueWork，实体 tick 亦在主线程，故用 HashMap。
 */
public final class FlightSeatInputStore {
    private FlightSeatInputStore() {}

    public record Input(int forward, int up) {
        public static final Input IDLE = new Input(0, 0);
    }

    private static final Map<UUID, Input> INPUTS = new HashMap<>();

    public static void set(UUID id, int forward, int up) {
        if (id == null) {
            return;
        }
        INPUTS.put(id, new Input(clamp(forward), clamp(up)));
    }

    public static Input get(UUID id) {
        Input input = id == null ? null : INPUTS.get(id);
        return input == null ? Input.IDLE : input;
    }

    public static void clear(UUID id) {
        if (id != null) {
            INPUTS.remove(id);
        }
    }

    public static int clamp(int value) {
        if (value < 0) {
            return -1;
        }
        return value > 0 ? 1 : 0;
    }
}
