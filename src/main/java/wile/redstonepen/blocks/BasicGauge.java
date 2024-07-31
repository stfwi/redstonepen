/*
 * @file BasicGauge.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import wile.redstonepen.libmc.StandardBlocks;


@SuppressWarnings("deprecation")
public class BasicGauge
{
  //--------------------------------------------------------------------------------------------------------------------
  // BasicGaugeBlock
  //--------------------------------------------------------------------------------------------------------------------

  public static class BasicGaugeBlock extends StandardBlocks.BaseBlock
  {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public BasicGaugeBlock(long config, BlockBehaviour.Properties properties)
    {
      super(config, properties.isRedstoneConductor(Blocks::never));
      registerDefaultState(defaultBlockState().setValue(POWER,0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    { super.createBlockStateDefinition(builder); builder.add(POWER); }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter source, BlockPos pos, CollisionContext selectionContext)
    { return Shapes.block(); }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos,  CollisionContext selectionContext)
    { return Shapes.block(); }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
      final BlockState state = super.getStateForPlacement(context);
      if(state == null) return null;
      return state.setValue(POWER, context.getLevel().getBestNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving)
    {
      if(world.isClientSide) return;
      final int p = world.getBestNeighborSignal(pos);
      if(p == state.getValue(POWER)) return;
      world.setBlock(pos, state.setValue(POWER, p), 2);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState fromState, LevelAccessor world_accessor, BlockPos pos, BlockPos fromPos)
    {
      if(!(world_accessor instanceof final ServerLevel world)) return state;
      return state.setValue(POWER, world.getBestNeighborSignal(pos));
    }

    // Forge Compliancy
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, @Nullable Direction side)
    { return true; }

    // Forge Compliancy
    public boolean shouldCheckWeakPower(BlockState state, LevelReader world, BlockPos pos, Direction side)
    { return false; }

  }

}
