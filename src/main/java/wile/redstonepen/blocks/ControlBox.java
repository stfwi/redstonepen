/*
 * @file ControlBox.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.blocks;

import net.minecraft.block.*;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;


public class ControlBox
{
  //--------------------------------------------------------------------------------------------------------------------
  // BridgeRelayBlock
  //--------------------------------------------------------------------------------------------------------------------

  public static class ControlBoxBlock extends CircuitComponents.DirectedComponentBlock
  {
    public ControlBoxBlock(long config, AbstractBlock.Properties builder, AxisAlignedBB[] aabb)
    { super(config, builder, aabb); }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, IBlockReader world, BlockPos pos, Direction redstone_side)
    {
      return 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getDirectSignal(BlockState state, IBlockReader world, BlockPos pos, Direction redsrone_side)
    { return getSignal(state, world, pos, redsrone_side); }

    @Override
    public BlockState update(BlockState state, World world, BlockPos pos, @Nullable BlockPos fromPos)
    { return state; }
  }
}
