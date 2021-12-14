/*
 * @file ControlBox.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;


public class ControlBox
{
  //--------------------------------------------------------------------------------------------------------------------
  // BridgeRelayBlock
  //--------------------------------------------------------------------------------------------------------------------

  public static class ControlBoxBlock extends CircuitComponents.DirectedComponentBlock
  {
    public ControlBoxBlock(long config, BlockBehaviour.Properties builder, AABB[] aabb)
    { super(config, builder, aabb); }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction redstone_side)
    {
      return 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction redsrone_side)
    { return getSignal(state, world, pos, redsrone_side); }

    @Override
    public BlockState update(BlockState state, Level world, BlockPos pos, @Nullable BlockPos fromPos)
    { return state; }
  }
}
