package com.wenxing.wenxingtools.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 文星生物蛋：以数据组件绑定任意实体 id，承载「模组未注册刷怪蛋的生物」。
 * 放生路径与原版 {@code SpawnEggItem#useOn} 同构（刷怪笼改类型 + 实体生成 + 失败不消耗）。
 */
public class GenericSpawnEggItem extends Item {

    public GenericSpawnEggItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(EntityType<?> type, int count) {
        ItemStack stack = new ItemStack(ModItems.GENERIC_SPAWN_EGG.get(), Math.max(1, count));
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id != null) {
            stack.set(ModItems.GENERIC_EGG_ENTITY.get(), id);
        }
        return stack;
    }

    @Nullable
    public static EntityType<?> getEntityType(ItemStack stack) {
        ResourceLocation id = stack.get(ModItems.GENERIC_EGG_ENTITY.get());
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.get(id);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        EntityType<?> type = getEntityType(stack);
        // 未绑定时不放生：原版会退化成物品的默认类型，这里宁可不动作
        if (type == null) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        BlockPos clickedPos = context.getClickedPos();
        Direction direction = context.getClickedFace();
        BlockState state = level.getBlockState(clickedPos);
        if (state.is(Blocks.SPAWNER)) {
            BlockEntity blockEntity = level.getBlockEntity(clickedPos);
            if (blockEntity instanceof SpawnerBlockEntity spawner) {
                spawner.setEntityId(type, level.getRandom());
                blockEntity.setChanged();
                level.sendBlockUpdated(clickedPos, state, state, 3);
                level.gameEvent(player, GameEvent.BLOCK_CHANGE, clickedPos);
                consume(stack, player);
                return InteractionResult.CONSUME;
            }
        }
        BlockPos spawnPos = state.getCollisionShape(level, clickedPos).isEmpty()
                ? clickedPos
                : clickedPos.relative(direction);
        if (type.spawn(serverLevel, stack, player, spawnPos, MobSpawnType.SPAWN_EGG, true,
                !Objects.equals(clickedPos, spawnPos) && direction == Direction.UP) != null) {
            consume(stack, player);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, clickedPos);
        }
        return InteractionResult.CONSUME;
    }

    private static void consume(ItemStack stack, @Nullable Player player) {
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        EntityType<?> type = getEntityType(stack);
        if (type != null) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            tooltip.add(Component.translatable("item.wenxingtools.generic_spawn_egg.entity",
                    id != null ? id.toString() : type.toString()));
            tooltip.add(Component.translatable("item.wenxingtools.generic_spawn_egg.spawner_hint"));
        } else {
            tooltip.add(Component.translatable("item.wenxingtools.generic_spawn_egg.unbound"));
        }
    }
}
