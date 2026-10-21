package com.wenxing.wenxingtools.block;

import com.wenxing.wenxingtools.util.ChunkScope;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 档位属性：取值域严格等于 {@link ChunkScope} 的三个档位（3 / 9 / 15），供火把与平台共用。
 *
 * <p>不用 {@code IntegerProperty}：它只能声明连续区间，取 [3,15] 会引入 10 个不可达值。
 * 一旦代码误写或存档被手动改成中间值，属性会接受、blockstate 缺对应变体、行为静默回退默认档——
 * 三处不一致且全程不报错。自定义属性让取值域与可达值一致：
 * 越界写入在 {@code setValue} 处立即抛错（开发期暴露），读档遇到域外值则 {@link #getValue}
 * 返回空、由编解码器降级为默认状态。
 *
 * <p>顺序即默认值来源：{@link #getPossibleValues()} 首项必须是 3×3，旧存档缺该属性时落到它。
 */
public final class ScopeProperty {

    public static final Property<Integer> SCOPE = new ScopePropertyValue();

    private ScopeProperty() {
    }

    private static final class ScopePropertyValue extends Property<Integer> {

        private static final List<Integer> VALUES =
                Arrays.stream(ChunkScope.values()).map(ChunkScope::chunks).toList();

        private ScopePropertyValue() {
            super("scope", Integer.class);
        }

        @Override
        public Collection<Integer> getPossibleValues() {
            return VALUES;
        }

        @Override
        public String getName(Integer value) {
            return Integer.toString(value);
        }

        @Override
        public Optional<Integer> getValue(String value) {
            try {
                int chunks = Integer.parseInt(value);
                // 取值域判定复用 ChunkScope（纯 JDK，可被单元测试覆盖）；域外值返回空，
                // 由 blockstate 编解码器降级为默认状态，而不是让 setValue 抛异常中断读档。
                return ChunkScope.isKnown(chunks) ? Optional.of(chunks) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }
}
