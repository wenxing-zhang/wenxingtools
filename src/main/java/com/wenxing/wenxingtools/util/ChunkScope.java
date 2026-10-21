package com.wenxing.wenxingtools.util;

/**
 * 区块档位：火把抑制范围与平台生成尺寸共用。3×3 / 9×9 / 15×15 区块。
 *
 * <p>刻意不实现 {@code StringRepresentable}：本仓库 {@code src/test} 的 classpath 只有 JUnit、
 * 没有 Minecraft，工具层一旦引入 MC 类型，档位与几何的边界就只能靠实机验证（违反 AGENTS §3.7
 * 的可验证性前置）。因此本枚举保持纯 JDK，BlockState 侧用 {@code IntegerProperty}
 * 承载 {@link #chunks()}，切档前再经 {@link #of} 还原。
 *
 * <p>枚举顺序即切档顺序；首项 {@link #THREE} 同时是属性默认值，也是旧存档缺该属性时的落点。
 */
public enum ChunkScope {
    THREE(3),
    NINE(9),
    FIFTEEN(15);

    private final int chunks;

    ChunkScope(int chunks) {
        this.chunks = chunks;
    }

    public int chunks() {
        return chunks;
    }

    public int radiusChunks() {
        return (chunks - 1) / 2;
    }

    public int blocks() {
        return chunks * 16;
    }

    /** 聊天栏文案用的「N乘N」 */
    public String label() {
        return chunks + "乘" + chunks;
    }

    public ChunkScope next() {
        ChunkScope[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** 由 BlockState 里的区块数还原档位；未知值（含旧存档缺属性）一律落到 {@link #THREE} */
    public static ChunkScope of(int chunks) {
        for (ChunkScope scope : values()) {
            if (scope.chunks == chunks) {
                return scope;
            }
        }
        return THREE;
    }
}
