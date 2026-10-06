package com.wenxing.wenxingtools.block;

import com.wenxing.wenxingtools.block.entity.CustomSpawnerBlockEntity;
import com.wenxing.wenxingtools.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CustomSpawnerBlock extends BaseEntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public CustomSpawnerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                net.minecraft.world.level.block.Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), 3);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        net.minecraft.world.entity.EntityType<?> spawnType = null;
        if (held.getItem() instanceof SpawnEggItem egg) {
            spawnType = egg.getType(null);
        } else if (held.is(com.wenxing.wenxingtools.item.ModItems.GENERIC_SPAWN_EGG.get())) {
            spawnType = com.wenxing.wenxingtools.item.GenericSpawnEggItem.getEntityType(held);
        }
        if (spawnType == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CustomSpawnerBlockEntity be) {
            be.setSpawnType(spawnType);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.0F);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("刷怪类型已切换"), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }


    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter world, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        BlockEntity te = world.getBlockEntity(pos);
        if (te != null) {
            stack.getOrCreateTag().put("BlockEntityTag", te.saveWithoutMetadata());
        }
        return stack;
    }


    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (world.isClientSide) {
            return;
        }
        BlockEntity te = world.getBlockEntity(pos);
        if (te != null && stack.hasTag() && stack.getTag().contains("BlockEntityTag", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            te.load(stack.getTag().getCompound("BlockEntityTag"));
            te.setChanged();
            world.sendBlockUpdated(pos, state, state, 3);
        }
    }


    @Override
    @Deprecated
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack tool = params.getParameter(LootContextParams.TOOL);
        if (tool != null && tool.getEnchantmentLevel(Enchantments.SILK_TOUCH) >= 1) {
            ItemStack drop = new ItemStack(this);
            BlockEntity te = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
            if (te != null) {
                drop.getOrCreateTag().put("BlockEntityTag", te.saveWithoutMetadata());
            }
            return List.of(drop);
        }
        return super.getDrops(state, params);
    }


    @Override
    public boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return true;
    }


    @Override
    public float getShadeBrightness(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CustomSpawnerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) {

            return createTickerHelper(blockEntityType, ModBlockEntities.CUSTOM_SPAWNER.get(), CustomSpawnerBlockEntity::clientTick);
        }
        return createTickerHelper(blockEntityType, ModBlockEntities.CUSTOM_SPAWNER.get(), CustomSpawnerBlockEntity::serverTick);
    }
}
