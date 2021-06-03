/*
 * @file RedstoneRelay.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.blocks;

import net.minecraft.block.*;
import net.minecraft.block.material.PushReaction;
import net.minecraft.entity.item.ItemEntity;
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
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.IBlockReader;
import net.minecraft.item.*;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import wile.redstonepen.ModContent;
import wile.redstonepen.ModRedstonePen;
import wile.redstonepen.libmc.blocks.StandardBlocks;
import wile.redstonepen.libmc.detail.Auxiliaries;

import javax.annotation.Nullable;
import java.util.*;


public class CircuitComponents
{
  //--------------------------------------------------------------------------------------------------------------------
  // DirectedComponentBlock
  //--------------------------------------------------------------------------------------------------------------------

  public static class DirectedComponentBlock extends StandardBlocks.WaterLoggable
  {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 3);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty STATE = IntegerProperty.create("state", 0, 1);
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

    protected static final VoxelShape mapped_shape(BlockState state, AxisAlignedBB[] aabb)
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

    public DirectedComponentBlock(long config, Block.Properties builder, AxisAlignedBB[] aabbs)
    {
      super(config, builder);
      setDefaultState(super.getDefaultState().with(FACING, Direction.NORTH).with(ROTATION,0).with(POWERED,false).with(STATE,0));
      stateContainer.getValidStates().forEach((state)->shapes_.put(state, mapped_shape(state, aabbs)));
    }

    public DirectedComponentBlock(long config, Block.Properties builder, AxisAlignedBB aabb)
    { this(config, builder, new AxisAlignedBB[]{aabb}); }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    { super.fillStateContainer(builder); builder.add(FACING, ROTATION, POWERED, STATE); }

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

    @Override
    @SuppressWarnings("deprecation")
    public int getComparatorInputOverride(BlockState state, World world, BlockPos pos)
    { return 0; }

    @Override
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
      state = state.with(FACING, face).with(ROTATION, rotation).with(POWERED, false).with(STATE,0);
      if(!isValidPosition(state, context.getWorld(), context.getPos())) return null;
      return state;
    }

    @Override
    public BlockState updatePostPlacement(BlockState state, Direction facing, BlockState facingState, IWorld world, BlockPos pos, BlockPos facingPos)
    {
      if((state=super.updatePostPlacement(state, facing, facingState, world, pos, facingPos)) == null) return state;
      if(!isValidPosition(state, world, pos)) return Blocks.AIR.getDefaultState();
      return (world instanceof ServerWorld) ? update(state, (ServerWorld)world, pos, facingPos) : state;
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
      if(!world.isRemote()) {
        notifyOutputNeighbourOfStateChange(state, world, pos);
        world.notifyNeighborsOfStateChange(pos, this);
      }
    }

    @Override
    public boolean shouldCheckWeakPower(BlockState state, IWorldReader world, BlockPos pos, Direction side)
    { return false; }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, World world, BlockPos pos, Block fromBlock, BlockPos fromPos, boolean isMoving)
    { update(state, world, pos, fromPos); }

    @OnlyIn(Dist.CLIENT)
    private void spawnPoweredParticle(World world, Random rand, BlockPos pos, Vector3f color, Direction side, float chance) {
      if(rand.nextFloat() < chance) {
        double c2 = chance * rand.nextFloat();
        double p0 = 0.5 + (side.getXOffset()*0.4) + (c2*.1);
        double p1 = 0.5 + (side.getYOffset()*0.4) + (c2*.1);
        double p2 = 0.5 + (side.getZOffset()*0.4) + (c2*.1);
        world.addParticle(new RedstoneParticleData(color.getX(),color.getY(),color.getZ(),1.0F), pos.getX()+p0, pos.getY()+p1, pos.getZ()+p2, 0, 0., 0);
      }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void animateTick(BlockState state, World world, BlockPos pos, Random rand)
    {
      if(!state.get(POWERED) || (rand.nextFloat() > 0.4)) return;
      final Vector3f color = new Vector3f(0.6f,0,0);
      Direction side = state.get(FACING);
      spawnPoweredParticle(world, rand, pos, color, side, 0.3f);
    }

    //------------------------------------------------------------------------------------------------------------------

    protected final Direction getOutputFacing(BlockState state)
    { return facing_mapping_.get((state.get(FACING).getIndex()) * 4 + state.get(ROTATION)); }

    protected final Direction getFrontFacing(BlockState state)
    { return facing_mapping_.get((state.get(FACING).getIndex()) * 4 + (((state.get(ROTATION)  )) & 0x3)); }

    protected final Direction getRightFacing(BlockState state)
    { return facing_mapping_.get((state.get(FACING).getIndex()) * 4 + (((state.get(ROTATION)+1)) & 0x3)); }

    protected final Direction getBackFacing(BlockState state)
    { return facing_mapping_.get((state.get(FACING).getIndex()) * 4 + (((state.get(ROTATION)+2)) & 0x3)); }

    protected final Direction getLeftFacing(BlockState state)
    { return facing_mapping_.get((state.get(FACING).getIndex()) * 4 + (((state.get(ROTATION)+3)) & 0x3)); }

    protected void notifyOutputNeighbourOfStateChange(BlockState state, World world, BlockPos pos)
    {
      final Direction facing = getOutputFacing(state);
      final BlockPos adjacent_pos = pos.offset(facing);
      final BlockState adjacent_state = world.getBlockState(adjacent_pos);
      try {
        adjacent_state.neighborChanged(world, adjacent_pos, this, pos, false);
        if(adjacent_state.shouldCheckWeakPower(world, adjacent_pos, facing)) {
          world.notifyNeighborsOfStateExcept(adjacent_pos, state.getBlock(), facing.getOpposite());
        }
      } catch(Throwable ex) {
        ModRedstonePen.logger().error("Track neighborChanged recursion detected, dropping!");
        Vector3d p = Vector3d.copyCentered(pos);
        world.addEntity(new ItemEntity(world, p.x, p.y, p.z, new ItemStack(this, 1)));
        world.setBlockState(pos, world.getBlockState(pos).getFluidState().getBlockState(), 2|16);
      }
    }

    public BlockState update(BlockState state, World world, BlockPos pos, @Nullable BlockPos fromPos)
    { return state; }

  }

  //--------------------------------------------------------------------------------------------------------------------
  // RelayBlock
  //--------------------------------------------------------------------------------------------------------------------

  public static class RelayBlock extends DirectedComponentBlock
  {
    protected boolean lock_update = false;

    protected boolean isPowered(BlockState state, World world, BlockPos pos)
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

    public RelayBlock(long config, Block.Properties builder, AxisAlignedBB aabb)
    { super(config, builder, aabb); }

    @Override
    @SuppressWarnings("deprecation")
    public int getWeakPower(BlockState state, IBlockReader world, BlockPos pos, Direction redsrone_side)
    { return ((!state.get(POWERED)) || (redsrone_side != getOutputFacing(state).getOpposite())) ? 0 : 15;}

    @Override
    @SuppressWarnings("deprecation")
    public int getStrongPower(BlockState state, IBlockReader world, BlockPos pos, Direction redsrone_side)
    { return getWeakPower(state, world, pos, redsrone_side); }

    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos pos, Random rnd)
    {
      final boolean powered = isPowered(state, world, pos);
      if(powered == state.get(POWERED)) return;
      if(!powered) {
        lock_update = true;
        world.setBlockState(pos, state.with(POWERED,false), 2|15);
        notifyOutputNeighbourOfStateChange(state, world, pos);
        lock_update = false;
      }
    }

    @Override
    public BlockState update(BlockState state, World world, BlockPos pos, @Nullable BlockPos fromPos)
    {
      final boolean powered = isPowered(state, world, pos);
      if(powered == state.get(POWERED)) return state;
      if(world.getPendingBlockTicks().isTickScheduled(pos, this)) return state;
      if(powered) {
        lock_update = true;
        world.setBlockState(pos, state.with(POWERED,true), 2|15);
        notifyOutputNeighbourOfStateChange(state, world, pos);
        lock_update = false;
      } else {
        world.getPendingBlockTicks().scheduleTick(pos, this, 2);
      }
      return state;
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // InvertedRelayBlock
  //--------------------------------------------------------------------------------------------------------------------

  public static class InvertedRelayBlock extends RelayBlock
  {
    private boolean lock_update = false;

    public InvertedRelayBlock(long config, Block.Properties builder, AxisAlignedBB aabb)
    { super(config, builder, aabb); }

    @Override
    @SuppressWarnings("deprecation")
    public int getWeakPower(BlockState state, IBlockReader world, BlockPos pos, Direction redsrone_side)
    { return (state.get(POWERED) || (redsrone_side != getOutputFacing(state).getOpposite())) ? 0 : 15; }

    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos pos, Random rnd)
    {
      final boolean powered = isPowered(state, world, pos);
      if(powered == state.get(POWERED)) return;
      if(powered) {
        lock_update = true;
        world.setBlockState(pos, state.with(POWERED,true), 2|15);
        notifyOutputNeighbourOfStateChange(state, world, pos);
        lock_update = false;
      }
    }

    @Override
    public BlockState update(BlockState state, World world, BlockPos pos, @Nullable BlockPos fromPos)
    {
      final boolean powered = isPowered(state, world, pos);
      if(powered == state.get(POWERED)) return state;
      if(world.getPendingBlockTicks().isTickScheduled(pos, this)) return state;
      if(powered) {
        world.getPendingBlockTicks().scheduleTick(pos, this, 2);
      } else {
        lock_update = true;
        world.setBlockState(pos, state.with(POWERED,false), 2|15);
        notifyOutputNeighbourOfStateChange(state, world, pos);
        lock_update = false;
      }
      return state;
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // BistableRelayBlock
  //--------------------------------------------------------------------------------------------------------------------

  public static class BistableRelayBlock extends RelayBlock
  {
    public BistableRelayBlock(long config, Block.Properties builder, AxisAlignedBB aabb)
    { super(config, builder, aabb); }

    @Override
    @SuppressWarnings("deprecation")
    public int getWeakPower(BlockState state, IBlockReader world, BlockPos pos, Direction redsrone_side)
    { return ((state.get(STATE) == 0) || (redsrone_side != getOutputFacing(state).getOpposite())) ? 0 : 15; }

    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos pos, Random rnd)
    {}

    @Override
    public BlockState update(BlockState state, World world, BlockPos pos, @Nullable BlockPos fromPos)
    {
      final boolean powered = isPowered(state, world, pos);
      final boolean pwstate = state.get(POWERED);
      if(powered == pwstate) return state;
      state = state.with(POWERED, powered);
      if(powered && !pwstate) {
        state = state.with(STATE, (state.get(STATE)==0) ? (1) : (0));
        world.setBlockState(pos, state, 2|15);
        notifyOutputNeighbourOfStateChange(state, world, pos);
      } else if(!powered && pwstate) {
        world.setBlockState(pos, state, 2|15);
      }
      return state;
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // PulseRelayBlock
  //--------------------------------------------------------------------------------------------------------------------

  public static class PulseRelayBlock extends RelayBlock
  {
    public PulseRelayBlock(long config, Block.Properties builder, AxisAlignedBB aabb)
    { super(config, builder, aabb); }

    @Override
    @SuppressWarnings("deprecation")
    public int getWeakPower(BlockState state, IBlockReader world, BlockPos pos, Direction redsrone_side)
    { return ((state.get(STATE) == 0) || (redsrone_side != getOutputFacing(state).getOpposite())) ? 0 : 15; }

    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos pos, Random rnd)
    {
      if(state.get(STATE) == 0) return;
      state = state.with(STATE, 0);
      world.setBlockState(pos, state, 2|15);
      notifyOutputNeighbourOfStateChange(state, world, pos);
    }

    @Override
    public BlockState update(BlockState state, World world, BlockPos pos, @Nullable BlockPos fromPos)
    {
      final boolean powered = isPowered(state, world, pos);
      if(powered != state.get(POWERED)) {
        state = state.with(POWERED, powered);
        if(powered) {
          boolean trig = (state.get(STATE) == 0);
          state = state.with(STATE, 1);
          world.setBlockState(pos, state, 2|15);
          if(trig) notifyOutputNeighbourOfStateChange(state, world, pos);
        } else {
          world.setBlockState(pos, state, 2|15);
        }
      }
      if(!world.getPendingBlockTicks().isTickScheduled(pos, this)) {
        world.getPendingBlockTicks().scheduleTick(pos, this, 2);
      }
      return state;
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // BridgeRelayBlock
  //--------------------------------------------------------------------------------------------------------------------

  public static class BridgeRelayBlock extends RelayBlock
  {
    private int power_update_recursion_level_ = 0;

    public BridgeRelayBlock(long config, Block.Properties builder, AxisAlignedBB aabb)
    { super(config, builder, aabb); }

    protected int getInputPower(World world, BlockPos relay_pos, Direction side)
    {
      final BlockPos pos = relay_pos.offset(side);
      final BlockState state = world.getBlockState(pos);
      int p = 0;
      if(power_update_recursion_level_ < 32) {
        ++power_update_recursion_level_;
        if(state.isIn(Blocks.REDSTONE_WIRE)) {
          p = Math.max(0, state.get(RedstoneWireBlock.POWER)-2);
        } else if(state.isIn(ModContent.TRACK_BLOCK)) {
          p = Math.max(0, RedstoneTrack.RedstoneTrackBlock.tile(world, pos).map((te->te.getRedstonePower(side, true))).orElse(0)-2);
        } else if(state.isIn(ModContent.BRIDGE_RELAY_BLOCK)) {
          if(state.get(FACING) != world.getBlockState(relay_pos).get(FACING)) {
            p = 0;
          } else if((state.get(ROTATION) & 0x1) != (world.getBlockState(relay_pos).get(ROTATION) & 0x1)) {
            p = 0;
          } else {
            p = getInputPower(world, pos, side);
          }
        } else {
          p = state.getWeakPower(world, pos, side);
          if((p<15) && (!state.canProvidePower()) && (state.shouldCheckWeakPower(world, pos, side))) {
            for(Direction d:Direction.values()) {
              if(d == side.getOpposite()) continue;
              p = Math.max(p, world.getBlockState(pos.offset(d)).getWeakPower(world, pos.offset(d), d));
              if(p >= 15) break;
            }
          }
        }
        if((--power_update_recursion_level_) < 0) power_update_recursion_level_ = 0;
      } else {
        System.out.println("recursion");
      }
      return p;
    }

    protected boolean isWireConnected(World world, BlockPos relay_pos, Direction side)
    {
      final BlockPos pos = relay_pos.offset(side);
      final BlockState state = world.getBlockState(pos);
      return state.isIn(Blocks.REDSTONE_WIRE) || state.isIn(ModContent.TRACK_BLOCK);
    }

    protected boolean isSidePowered(World world, BlockPos pos, Direction side)
    { return world.getRedstonePower(pos.offset(side), side) > 0; }

    protected boolean isPowered(BlockState state, World world, BlockPos pos)
    { return isSidePowered(world, pos, state.get(FACING)) || isSidePowered(world, pos, getOutputFacing(state).getOpposite()); }

    @Override
    @SuppressWarnings("deprecation")
    public int getWeakPower(BlockState state, IBlockReader world, BlockPos pos, Direction redstone_side)
    {
      if((redstone_side == getOutputFacing(state).getOpposite())) return state.get(POWERED) ? 15 : 0;
      int p = 0;
      final Direction left = getLeftFacing(state);
      final Direction right = getRightFacing(state);
      if(((redstone_side == left) || (redstone_side == right)) && (world instanceof ServerWorld)) {
        final boolean left_source = !isWireConnected((ServerWorld)world, pos, left) && world.getBlockState(pos.offset(left)).canProvidePower();
        final boolean right_source = !isWireConnected((ServerWorld)world, pos, right) && world.getBlockState(pos.offset(right)).canProvidePower();
        if(left_source && !right_source) {
          p = getInputPower((ServerWorld)world, pos, left);
        } else if(!left_source && right_source) {
          p = getInputPower((ServerWorld)world, pos, right);
        } else {
          p = Math.max(0, Math.max(getInputPower((ServerWorld)world, pos, left), getInputPower((ServerWorld)world, pos, right)));
        }
        //System.out.println("p@" + redstone_side.getOpposite() + ":" + p);
      }
      return p;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getStrongPower(BlockState state, IBlockReader world, BlockPos pos, Direction redsrone_side)
    { return getWeakPower(state, world, pos, redsrone_side); }

    @Override
    public BlockState update(BlockState state, World world, BlockPos pos, @Nullable BlockPos fromPos)
    {
      // Relay branch update
      {
        final boolean powered = isPowered(state, world, pos);
        if(powered != state.get(POWERED)) {
          if(!world.getPendingBlockTicks().isTickScheduled(pos, this)) {
            if(powered) {
              lock_update = true;
              world.setBlockState(pos, (state=state.with(POWERED,true)), 2|15);
              world.neighborChanged(pos.offset(getOutputFacing(state)), this, pos);
              lock_update = false;
            } else {
              world.getPendingBlockTicks().scheduleTick(pos, this, 2);
            }
          }
        }
      }
      // Wire branch update
      if(fromPos != null) {
        final Vector3i v = pos.subtract(fromPos);
        final Direction dir = Direction.getFacingFromVector(v.getX(), v.getY(), v.getZ());
        final Direction left = getLeftFacing(state);
        final Direction right = getRightFacing(state);
        if((dir == left) || (dir == right)) {
          lock_update = true;
          power_update_recursion_level_ = 0;
          int pr = getWeakPower(state, world, pos, right);
          int pl = getWeakPower(state, world, pos, left);
          final boolean track_powered = (pr>0) || (pl>0);
          //System.out.println("u->" + dir + " track_powered:" + track_powered);
          if(track_powered != (state.get(STATE)==1)) world.setBlockState(pos, (state=state.with(STATE, track_powered?1:0)), 2|15);
          world.neighborChanged(pos.offset(dir), this, pos);
          lock_update = false;
        }
      }
      return state;
    }
  }
}
