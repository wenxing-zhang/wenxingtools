package com.wenxing.wenxingtools.util;

public final class ModEnums {
    private ModEnums() {}

    public enum CharacteristicKey {
        INVINCIBLE(1),
        RESOURCE(2),
        FREEDOM(3),
        // networkId=4 为历史保留空位，禁止复用，避免旧客户端错解
        KILL_AURA(5),
        /** 资源增幅附魔开关（非 Authority 特性） */
        RESOURCE_AMP(6);

        private static final CharacteristicKey[] BY_ORDINAL = values();
        private static final CharacteristicKey[] BY_NETWORK_ID;
        private static final int MAX_NETWORK_ID;

        static {
            int max = 0;
            for (CharacteristicKey k : BY_ORDINAL) {
                if (k.networkId > max) {
                    max = k.networkId;
                }
            }
            MAX_NETWORK_ID = max;
            BY_NETWORK_ID = new CharacteristicKey[max + 1];
            for (CharacteristicKey k : BY_ORDINAL) {
                BY_NETWORK_ID[k.networkId] = k;
            }
        }

        private final int networkId;

        CharacteristicKey(int networkId) {
            this.networkId = networkId;
        }

        public int networkId() {
            return networkId;
        }

        public static CharacteristicKey fromNetworkId(int id) {
            if (id < 1 || id > MAX_NETWORK_ID) {
                return null;
            }
            return BY_NETWORK_ID[id];
        }
    }

    public enum KillAuraMode {
        ALL(0),
        HOSTILE(1);

        private final int persistentId;

        KillAuraMode(int persistentId) {
            this.persistentId = persistentId;
        }

        public int persistentId() {
            return persistentId;
        }

        public static KillAuraMode fromPersistentId(int id) {
            return id == HOSTILE.persistentId ? HOSTILE : ALL;
        }

        public String translationKey() {
            return this == HOSTILE
                    ? "msg.wenxingtools.kill_aura.mode.hostile"
                    : "msg.wenxingtools.kill_aura.mode.all";
        }
    }
}
