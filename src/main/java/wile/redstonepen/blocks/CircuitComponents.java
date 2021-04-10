/*
 * @file RedstoneRelay.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.blocks;

import net.minecraft.block.*;
import net.minecraft.block.material.PushReaction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particles.RedstoneParticleData;
import net.minecraft.pathfinding.PathType;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.world.IBlockReader;
import net.minecraft.item.*;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import wile.redstonepen.libmc.blocks.StandardBlocks;
import wile.redstonepen.libmc.detail.Auxiliaries;

import javax.annotation.Nullable;
import java.util.*;


public class CircuitComponents
{
  //--------------------------------------------------------------------------------------------------------------------
  // Definitions
  //--------------------------------------------------------------------------------------------------------------------

  public static class SingleOutputComponent extends StandardBlocks.WaterLoggable
  {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 3);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    protected final Map<BlockState, VoxelShape> shapes_ = new HashMap<>();

    protected static final List<Direction> facing_mapping_ = new ArrayList<>();

    static {
      Arrays.stream(Direction.values()).forEach((face)->{
        switch(face) {
          case DOWN:
            facing_mapping_.add(Direction.NORTH);
            facing_mapping_.add(Direction.EAST);
            facing_mapping_.add(Direction.SOUTH);
            facing_mapping_.add(Direction.WEST);
            break;
          case UP:
            facing_mapping_.add(Direction.NORTH);
            facing_mapping_.add(Direction.EAST);
            facing_mapping_.add(Direction.SOUTH);
            facing_mapping_.add(Direction.WEST);
            break;
          case NORTH:
            facing_mapping_.add(Direction.UP);
            facing_mapping_.add(Direction.EAST);
            facing_mapping_.add(Direction.DOWN);
            facing_mapping_.add(Direction.WEST);
            break;
          case EAST:
            facing_mapping_.add(Direction.UP);
            facing_mapping_.add(Direction.SOUTH);
            facing_mapping_.add(Direction.DOWN);
            facing_mapping_.add(Direction.NORTH);
            break;
          case SOUTH:
            facing_mapping_.add(Direction.UP);
            facing_mapping_.add(Direction.WEST);
            facing_mapping_.add(Direction.DOWN);
            facing_mapping_.add(Direction.EAST);
            break;
          case WEST:
            facing_mapping_.add(Direction.UP);
            facing_mapping_.add(Direction.NORTH);
            facing_mapping_.add(Direction.DOWN);
            facing_mapping_.add(Direction.SOUTH);
            break;
        }
      });
    }

    protected static final VoxelShape mapped_shape(BlockState state, AxisAlignedBB aabb)
    {
      switch(state.get(FACING)) {
        case DOWN:
          switch(state.get(ROTATION)) {
            case 0: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(aabb, 0));
            case 1: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(aabb, 1));
            case 2: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(aabb, 2));
            case 3: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(aabb, 3));
          }
        case UP:
          switch(state.get(ROTATION)) {
            case 0: return Auxiliaries.getUnionShape(Auxiliaries.getMirroredAABB(Auxiliaries.getYRotatedAABB(aabb, 0), Direction.Axis.Y));
            case 1: return Auxiliaries.getUnionShape(Auxiliaries.getMirroredAABB(Auxiliaries.getYRotatedAABB(aabb, 1), Direction.Axis.Y));
            case 2: return Auxiliaries.getUnionShape(Auxiliaries.getMirroredAABB(Auxiliaries.getYRotatedAABB(aabb, 2), Direction.Axis.Y));
            case 3: return Auxiliaries.getUnionShape(Auxiliaries.getMirroredAABB(Auxiliaries.getYRotatedAABB(aabb, 3), Direction.Axis.Y));
          }
        case NORTH:
          switch(state.get(ROTATION)) {
            case 0: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.SOUTH), Direction.DOWN));
            case 1: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.WEST), Direction.DOWN));
            case 2: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.NORTH), Direction.DOWN));
            case 3: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.EAST), Direction.DOWN));
          }
        case EAST:
          switch(state.get(ROTATION)) {
            case 0: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.UP), Direction.WEST), 0));
            case 1: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.WEST), Direction.DOWN), 1));
            case 2: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.SOUTH), Direction.UP), 3));
            case 3: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.WEST), Direction.UP), 3));
          }
        case SOUTH:
          switch(state.get(ROTATION)) {
            case 0: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.NORTH), Direction.UP));
            case 1: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.EAST), Direction.UP));
            case 2: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.SOUTH), Direction.UP));
            case 3: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.WEST), Direction.UP));
          }
        case WEST:
          switch(state.get(ROTATION)) {
            case 0: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.UP), Direction.EAST));
            case 1: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.EAST), Direction.UP), 1));
            case 2: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.DOWN), Direction.WEST));
            case 3: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.WEST), Direction.UP), 1));
          }
      }
      return VoxelShapes.fullCube();
    }

    public SingleOutputComponent(long config, Block.Properties builder, AxisAlignedBB aabb)
    {
      super(config, builder);
      setDefaultState(super.getDefaultState().with(FACING, Direction.NORTH).with(ROTATION,0).with(POWERED,false));
      stateContainer.getValidStates().forEach((state)->shapes_.put(state, mapped_shape(state, aabb)));
    }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    { super.fillStateContainer(builder); builder.add(FACING, ROTATION, POWERED); }

    @Override
    public boolean hasTileEntity(BlockState state)
    { return false; }

    @Override
    public boolean hasDynamicDropList()
    { return true; }

    @Override
    public List<ItemStack> dropList(BlockState state, World world, @Nullable TileEntity te, boolean explosion)
    { return Collections.singletonList(new ItemStack(this.asItem())); }

    @Override
    public boolean allowsMovement(BlockState state, IBlockReader world, BlockPos pos, PathType type)
    { return true; }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext selectionContext)
    { return shapes_.getOrDefault(state, VoxelShapes.fullCube()); }

    @Override
    public boolean propagatesSkylightDown(BlockState state, IBlockReader reader, BlockPos pos)
    { return !state.get(WATERLOGGED); }

    @Override
    public PushReaction getPushReaction(BlockState state)
    { return PushReaction.DESTROY; }

    @Deprecated
    public boolean canConnectRedstone(BlockState state, IBlockReader world, BlockPos pos, @Nullable Direction side)
    { return (side==null) || (side != state.get(FACING)); }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canProvidePower(BlockState state)
    { return true; }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasComparatorInputOverride(BlockState state)
    { return false; }

    @Deprecated
    @SuppressWarnings("deprecation")
    public int getComparatorInputOverride(BlockState state, World world, BlockPos pos)
    { return 0; }

    @Deprecated
    @SuppressWarnings("deprecation")
    public int getWeakPower(BlockState state, IBlockReader world, BlockPos pos, Direction redsrone_side)
    { return 0; }

    @Deprecated
    @SuppressWarnings("deprecation")
    public int getStrongPower(BlockState state, IBlockReader world, BlockPos pos, Direction redsrone_side)
    { return 0; }

    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos pos, Random rnd)
    {}

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockItemUseContext context)
    {
      BlockState state = super.getStateForPlacement(context);
      if(state==null) return state;
      final Direction face = context.getFace().getOpposite();
      Vector3d hit = context.getHitVec().subtract(Vector3d.copyCentered(context.getPos()));
      switch(face) {
        case WEST:  case EAST:  hit = hit.mul(0, 1, 1); break;
        case SOUTH: case NORTH: hit = hit.mul(1, 1, 0); break;
        default:                hit = hit.mul(1, 0, 1); break;
      }
      final Direction dir = Direction.getFacingFromVector(hit.getX(), hit.getY(), hit.getZ());
      int rotation = 0;
      switch(face) {
        case DOWN:
        case UP:
          switch(dir) {
            case EAST:  rotation=1; break;
            case SOUTH: rotation=2; break;
            case WEST:  rotation=3; break;
            default:
          }
          break;
        case NORTH:
          switch(dir) {
            case EAST: rotation=1; break;
            case DOWN: rotation=2; break;
            case WEST: rotation=3; break;
            default:
          }
          break;
        case EAST:
          switch(dir) {
            case SOUTH: rotation=1; break;
            case DOWN:  rotation=2; break;
            case NORTH: rotation=3; break;
            default:
          }
          break;
        case SOUTH:
          switch(dir) {
            case WEST:  rotation=1; break;
            case DOWN:  rotation=2; break;
            case EAST:  rotation=3; break;
            default:
          }
          break;
        case WEST:
          switch(dir) {
            case NORTH: rotation=1; break;
            case DOWN:  rotation=2; break;
            case SOUTH: rotation=3; break;
            default:
          }
          break;
      }
      state = state.with(FACING, face).with(ROTATION, rotation).with(POWERED, false);
      if(!isValidPosition(state, context.getWorld(), context.getPos())) return null;
      return state;
    }

    @Override
    public BlockState updatePostPlacement(BlockState state, Direction facing, BlockState facingState, IWorld world, BlockPos pos, BlockPos facingPos)
    {
      if((state=super.updatePostPlacement(state, facing, facingState, world, pos, facingPos)) == null) return state;
      if(!isValidPosition(state, world, pos)) return Blocks.AIR.getDefaultState();
      if(!world.isRemote()) world.getPendingBlockTicks().scheduleTick(pos, this, 1);
      return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isValidPosition(BlockState state, IWorldReader world, BlockPos pos)
    {
      final Direction face = state.get(FACING);
      final BlockPos adj_pos = pos.offset(face);
      return world.getBlockState(adj_pos).isSolidSide(world, adj_pos, face.getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean isMoving)
    { update(state, world, pos, null); }

    @Override
    @SuppressWarnings("deprecation")
    public void onReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving)
    {
      if(isMoving || state.isIn(newState.getBlock())) return;
      super.onReplaced(state, world, pos, newState, isMoving);
      if(!world.isRemote()) world.notifyNeighborsOfStateChange(pos, this);
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult rtr)
    { return ActionResultType.PASS; }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, World world, BlockPos pos, Block fromBlock, BlockPos fromPos, boolean isMoving)
    { update(state, world, pos, fromPos); }

    @OnlyIn(Dist.CLIENT)
    private void spawnPoweredParticle(World world, Random rand, BlockPos pos, Vector3f color, Direction from, Direction to, float minChance, float maxChance) {
      float f = maxChance - minChance;
      if(rand.nextFloat() < 0.3f * f) {
        double c1 = 0.4375;
        double c2 = minChance + f * rand.nextFloat();
        double p0 = 0.5 + (c1 * from.getXOffset()) + (c2*.1 * to.getXOffset());
        double p1 = 0.5 + (c1 * from.getYOffset()) + (c2*.1 * to.getYOffset());
        double p2 = 0.5 + (c1 * from.getZOffset()) + (c2*.1 * to.getZOffset());
        world.addParticle(new RedstoneParticleData(color.getX(),color.getY(),color.getZ(),1.0F), pos.getX()+p0, pos.getY()+p1, pos.getZ()+p2, 0, 0., 0);
      }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void animateTick(BlockState state, World world, BlockPos pos, Random rand)
    {
      if(!state.get(POWERED) || (rand.nextFloat() > 0.4)) return;
      final Vector3f color = new Vector3f(0.6f,0,0);
      Direction side = Direction.NORTH;
      spawnPoweredParticle(world, rand, pos, color, side, side.getOpposite(), -0.5F, 0.5F);
    }

    //------------------------------------------------------------------------------------------------------------------

    protected final Direction getOutputFacing(BlockState state)
    { return facing_mapping_.get((state.get(FACING).getIndex()) * 4 + state.get(ROTATION)); }

    public void update(BlockState state, World world, BlockPos pos, @Nullable BlockPos fromPos)
    {}

  }


  //--------------------------------------------------------------------------------------------------------------------
  // Block
  //--------------------------------------------------------------------------------------------------------------------

  public static class RelayBlock extends SingleOutputComponent
  {
    public RelayBlock(long config, Block.Properties builder, AxisAlignedBB aabb)
    { super(config, builder, aabb); }

    @Deprecated
    @SuppressWarnings("deprecation")
    public int getWeakPower(BlockState state, IBlockReader world, BlockPos pos, Direction redsrone_side)
    {
      if(!state.get(POWERED)) return 0;
      if(redsrone_side != getOutputFacing(state).getOpposite()) return 0;
      return 15;
    }

    @Deprecated
    @SuppressWarnings("deprecation")
    public int getStrongPower(BlockState state, IBlockReader world, BlockPos pos, Direction redsrone_side)
    { return getWeakPower(state, world, pos, redsrone_side); }

    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos pos, Random rnd)
    {
      if(!isPowered(state, world, pos)) {
        world.setBlockState(pos, state.with(POWERED,false), 2|15);
      }
    }

    // ---------------------------------------------------------------------------------------------------

    private boolean isPowered(BlockState state, World world, BlockPos pos)
    {
      final Direction output_side = getOutputFacing(state);
      final Direction mount_side = state.get(FACING);
      for(Direction side:Direction.values()) {
        if(side == output_side) continue;
        if(side == mount_side.getOpposite()) continue;
        if(world.getRedstonePower(pos.offset(side), side) > 0) return true;
      }
      return false;
    }

    @Override
    public void update(BlockState state, World world, BlockPos pos, @Nullable BlockPos fromPos)
    {
      boolean is_powered = state.get(POWERED);
      if(isPowered(state, world, pos)) {
        if(!is_powered) {
          world.setBlockState(pos, state.with(POWERED,true), 2|15);
        }
      } else {
        if(is_powered) {
          if(!world.getPendingBlockTicks().isTickScheduled(pos, this)) {
            world.getPendingBlockTicks().scheduleTick(pos, this, 2);
          }
        }
      }
    }

  }

}
