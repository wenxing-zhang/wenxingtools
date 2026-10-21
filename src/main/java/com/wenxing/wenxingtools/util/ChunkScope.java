package com.wenxing.wenxingtools.util;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * 区块档位：火把抑制范围与平台生成尺寸共用。3×3 / 9×9 / 15×15 区块。
 *
 * <p>枚举顺序即切档顺序；首项 {@link #THREE} 同时是 BlockState 默认值，也是旧存档
 * 缺少该属性时的落点。实现 {@link StringRepresentable} 是 {@code EnumProperty} 的
 * 泛型约束——序列化名决定 blockstate JSON 的变体键（{@code scope=three} 等）。
 */
public enum ChunkScope implements StringRepresentable {
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

    public String label() {
        return chunks + "乘" + chunks;
    }

    public ChunkScope next() {
        ChunkScope[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
