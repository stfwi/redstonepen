/*
 * @file RedstoneRelay.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.blocks;

import com.mojang.math.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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
        switch (face) {
          case DOWN, UP -> {
            facing_mapping_.add(Direction.NORTH);
            facing_mapping_.add(Direction.EAST);
            facing_mapping_.add(Direction.SOUTH);
            facing_mapping_.add(Direction.WEST);
          }
          case NORTH -> {
            facing_mapping_.add(Direction.UP);
            facing_mapping_.add(Direction.EAST);
            facing_mapping_.add(Direction.DOWN);
            facing_mapping_.add(Direction.WEST);
          }
          case EAST -> {
            facing_mapping_.add(Direction.UP);
            facing_mapping_.add(Direction.SOUTH);
            facing_mapping_.add(Direction.DOWN);
            facing_mapping_.add(Direction.NORTH);
          }
          case SOUTH -> {
            facing_mapping_.add(Direction.UP);
            facing_mapping_.add(Direction.WEST);
            facing_mapping_.add(Direction.DOWN);
            facing_mapping_.add(Direction.EAST);
          }
          case WEST -> {
            facing_mapping_.add(Direction.UP);
            facing_mapping_.add(Direction.NORTH);
            facing_mapping_.add(Direction.DOWN);
            facing_mapping_.add(Direction.SOUTH);
          }
        }
      });
    }

    protected static VoxelShape mapped_shape(BlockState state, AABB[] aabb)
    {
      switch(state.getValue(FACING)) {
        case DOWN:
          switch(state.getValue(ROTATION)) {
            case 0: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(aabb, 0));
            case 1: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(aabb, 1));
            case 2: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(aabb, 2));
            case 3: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(aabb, 3));
          }
        case UP:
          switch(state.getValue(ROTATION)) {
            case 0: return Auxiliaries.getUnionShape(Auxiliaries.getMirroredAABB(Auxiliaries.getYRotatedAABB(aabb, 0), Direction.Axis.Y));
            case 1: return Auxiliaries.getUnionShape(Auxiliaries.getMirroredAABB(Auxiliaries.getYRotatedAABB(aabb, 1), Direction.Axis.Y));
            case 2: return Auxiliaries.getUnionShape(Auxiliaries.getMirroredAABB(Auxiliaries.getYRotatedAABB(aabb, 2), Direction.Axis.Y));
            case 3: return Auxiliaries.getUnionShape(Auxiliaries.getMirroredAABB(Auxiliaries.getYRotatedAABB(aabb, 3), Direction.Axis.Y));
          }
        case NORTH:
          switch(state.getValue(ROTATION)) {
            case 0: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.SOUTH), Direction.DOWN));
            case 1: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.WEST), Direction.DOWN));
            case 2: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.NORTH), Direction.DOWN));
            case 3: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.EAST), Direction.DOWN));
          }
        case EAST:
          switch(state.getValue(ROTATION)) {
            case 0: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.UP), Direction.WEST), 0));
            case 1: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.WEST), Direction.DOWN), 1));
            case 2: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.SOUTH), Direction.UP), 3));
            case 3: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.WEST), Direction.UP), 3));
          }
        case SOUTH:
          switch(state.getValue(ROTATION)) {
            case 0: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.NORTH), Direction.UP));
            case 1: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.EAST), Direction.UP));
            case 2: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.SOUTH), Direction.UP));
            case 3: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.WEST), Direction.UP));
          }
        case WEST:
          switch(state.getValue(ROTATION)) {
            case 0: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.UP), Direction.EAST));
            case 1: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.EAST), Direction.UP), 1));
            case 2: return Auxiliaries.getUnionShape(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.DOWN), Direction.WEST));
            case 3: return Auxiliaries.getUnionShape(Auxiliaries.getYRotatedAABB(Auxiliaries.getRotatedAABB(Auxiliaries.getRotatedAABB(aabb, Direction.WEST), Direction.UP), 1));
          }
      }
      return Shapes.block();
    }

    public DirectedComponentBlock(long config, BlockBehaviour.Properties builder, AABB[] aabbs)
    {
      super(config, builder);
      registerDefaultState(super.defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ROTATION,0).setValue(POWERED,false).setValue(STATE,0));
      stateDefinition.getPossibleStates().forEach((state)->shapes_.put(state, mapped_shape(state, aabbs)));
    }

    public DirectedComponentBlock(long config, BlockBehaviour.Properties builder, AABB aabb)
    { this(config, builder, new AABB[]{aabb}); }

    //------------------------------------------------------------------------------------------------------------------

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    { super.createBlockStateDefinition(builder); builder.add(FACING, ROTATION, POWERED, STATE); }

    @Override
    public boolean hasDynamicDropList()
    { return true; }

    @Override
    public List<ItemStack> dropList(BlockState state, Level world, @Nullable BlockEntity te, boolean explosion)
    { return Collections.singletonList(new ItemStack(this.asItem())); }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type)
    { return true; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    { return shapes_.getOrDefault(state, Shapes.block()); }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos)
    { return !state.getValue(WATERLOGGED); }

    @Override
    public PushReaction getPistonPushReaction(BlockState state)
    { return PushReaction.DESTROY; }

    @Deprecated
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, @Nullable Direction side)
    { return (side==null) || (side != state.getValue(FACING)); }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(BlockState state)
    { return true; }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(BlockState state)
    { return false; }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos)
    { return 0; }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction redsrone_side)
    { return 0; }

    @Deprecated
    @SuppressWarnings("deprecation")
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction redsrone_side)
    { return 0; }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, Random rnd)
    {}

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
      BlockState state = super.getStateForPlacement(context);
      if(state==null) return state;
      final Direction face = context.getClickedFace().getOpposite();
      final Vec3 hit_r = context.getClickLocation().subtract(Vec3.atCenterOf(context.getClickedPos()));
      final Vec3 hit = switch(face) {
        case WEST, EAST -> hit_r.multiply(0, 1, 1);
        case SOUTH, NORTH -> hit_r.multiply(1, 1, 0);
        default -> hit_r.multiply(1, 0, 1);
      };
      final Direction dir = Direction.getNearest(hit.x(), hit.y(), hit.z());
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
      state = state.setValue(FACING, face).setValue(ROTATION, rotation).setValue(POWERED, false).setValue(STATE,0);
      if(!canSurvive(state, context.getLevel(), context.getClickedPos())) return null;
      return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor world, BlockPos pos, BlockPos facingPos)
    {
      if((state=super.updateShape(state, facing, facingState, world, pos, facingPos)) == null) return state;
      if(!canSurvive(state, world, pos)) return Blocks.AIR.defaultBlockState();
      return (world instanceof ServerLevel) ? update(state, (ServerLevel)world, pos, facingPos) : state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos)
    {
      final Direction face = state.getValue(FACING);
      final BlockPos adj_pos = pos.relative(face);
      return world.getBlockState(adj_pos).isFaceSturdy(world, adj_pos, face.getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving)
    { update(state, world, pos, null); }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving)
    {
      if(isMoving || state.is(newState.getBlock())) return;
      super.onRemove(state, world, pos, newState, isMoving);
      if(!world.isClientSide()) {
        notifyOutputNeighbourOfStateChange(state, world, pos);
        world.updateNeighborsAt(pos, this);
      }
    }

    @Override
    public boolean shouldCheckWeakPower(BlockState state, LevelReader world, BlockPos pos, Direction side)
    { return false; }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block fromBlock, BlockPos fromPos, boolean isMoving)
    { update(state, world, pos, fromPos); }

    @OnlyIn(Dist.CLIENT)
    private void spawnPoweredParticle(Level world, Random rand, BlockPos pos, Vec3 color, Direction side, float chance) {
      if(rand.nextFloat() < chance) {
        double c2 = chance * rand.nextFloat();
        double p0 = 0.5 + (side.getStepX()*0.4) + (c2*.1);
        double p1 = 0.5 + (side.getStepY()*0.4) + (c2*.1);
        double p2 = 0.5 + (side.getStepZ()*0.4) + (c2*.1);
        world.addParticle(new DustParticleOptions(new Vector3f(color),1.0F), pos.getX()+p0, pos.getY()+p1, pos.getZ()+p2, 0, 0., 0);
      }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, Random rand)
    {
      if(!state.getValue(POWERED) || (rand.nextFloat() > 0.4)) return;
      final Vec3 color = new Vec3(0.6f,0,0);
      Direction side = state.getValue(FACING);
      spawnPoweredParticle(world, rand, pos, color, side, 0.3f);
    }

    //------------------------------------------------------------------------------------------------------------------

    protected final Direction getOutputFacing(BlockState state)
    { return facing_mapping_.get((state.getValue(FACING).get3DDataValue()) * 4 + state.getValue(ROTATION)); }

    protected final Direction getFrontFacing(BlockState state)
    { return facing_mapping_.get((state.getValue(FACING).get3DDataValue()) * 4 + (((state.getValue(ROTATION)  )) & 0x3)); }

    protected final Direction getRightFacing(BlockState state)
    { return facing_mapping_.get((state.getValue(FACING).get3DDataValue()) * 4 + (((state.getValue(ROTATION)+1)) & 0x3)); }

    protected final Direction getBackFacing(BlockState state)
    { return facing_mapping_.get((state.getValue(FACING).get3DDataValue()) * 4 + (((state.getValue(ROTATION)+2)) & 0x3)); }

    protected final Direction getLeftFacing(BlockState state)
    { return facing_mapping_.get((state.getValue(FACING).get3DDataValue()) * 4 + (((state.getValue(ROTATION)+3)) & 0x3)); }

    protected void notifyOutputNeighbourOfStateChange(BlockState state, Level world, BlockPos pos)
    {
      final Direction facing = getOutputFacing(state);
      final BlockPos adjacent_pos = pos.relative(facing);
      final BlockState adjacent_state = world.getBlockState(adjacent_pos);
      try {
        adjacent_state.neighborChanged(world, adjacent_pos, this, pos, false);
        if(adjacent_state.shouldCheckWeakPower(world, adjacent_pos, facing)) {
          world.updateNeighborsAtExceptFromFacing(adjacent_pos, state.getBlock(), facing.getOpposite());
        }
      } catch(Throwable ex) {
        ModRedstonePen.logger().error("Track neighborChanged recursion detected, dropping!");
        Vec3 p = Vec3.atCenterOf(pos);
        world.addFreshEntity(new ItemEntity(world, p.x, p.y, p.z, new ItemStack(this, 1)));
        world.setBlock(pos, world.getBlockState(pos).getFluidState().createLegacyBlock(), 2|16);
      }
    }

    public BlockState update(BlockState state, Level world, BlockPos pos, @Nullable BlockPos fromPos)
    { return state; }

  }

  //--------------------------------------------------------------------------------------------------------------------
  // RelayBlock
  //--------------------------------------------------------------------------------------------------------------------

  public static class RelayBlock extends DirectedComponentBlock
  {
    protected boolean lock_update = false;

    protected boolean isPowered(BlockState state, Level world, BlockPos pos)
    {
      final Direction output_side = getOutputFacing(state);
      final Direction mount_side = state.getValue(FACING);
      for(Direction side:Direction.values()) {
        if(side == output_side) continue;
        if(side == mount_side.getOpposite()) continue;
        if(world.getSignal(pos.relative(side), side) > 0) return true;
      }
      return false;
    }

    public RelayBlock(long config, BlockBehaviour.Properties builder, AABB aabb)
    { super(config, builder, aabb); }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction redsrone_side)
    { return ((!state.getValue(POWERED)) || (redsrone_side != getOutputFacing(state).getOpposite())) ? 0 : 15;}

    @Override
    @SuppressWarnings("deprecation")
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction redsrone_side)
    { return getSignal(state, world, pos, redsrone_side); }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, Random rnd)
    {
      final boolean powered = isPowered(state, world, pos);
      if(powered == state.getValue(POWERED)) return;
      if(!powered) {
        lock_update = true;
        world.setBlock(pos, state.setValue(POWERED,false), 2|15);
        notifyOutputNeighbourOfStateChange(state, world, pos);
        lock_update = false;
      }
    }

    @Override
    public BlockState update(BlockState state, Level world, BlockPos pos, @Nullable BlockPos fromPos)
    {
      final boolean powered = isPowered(state, world, pos);
      if(powered == state.getValue(POWERED)) return state;
      if(world.getBlockTicks().hasScheduledTick(pos, this)) return state;
      if(powered) {
        lock_update = true;
        world.setBlock(pos, state.setValue(POWERED,true), 2|15);
        notifyOutputNeighbourOfStateChange(state, world, pos);
        lock_update = false;
      } else {
        world.scheduleTick(pos, this, 2);
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

    public InvertedRelayBlock(long config, BlockBehaviour.Properties builder, AABB aabb)
    { super(config, builder, aabb); }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction redsrone_side)
    { return (state.getValue(POWERED) || (redsrone_side != getOutputFacing(state).getOpposite())) ? 0 : 15; }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, Random rnd)
    {
      final boolean powered = isPowered(state, world, pos);
      if(powered == state.getValue(POWERED)) return;
      if(powered) {
        lock_update = true;
        world.setBlock(pos, state.setValue(POWERED,true), 2|15);
        notifyOutputNeighbourOfStateChange(state, world, pos);
        lock_update = false;
      }
    }

    @Override
    public BlockState update(BlockState state, Level world, BlockPos pos, @Nullable BlockPos fromPos)
    {
      final boolean powered = isPowered(state, world, pos);
      if(powered == state.getValue(POWERED)) return state;
      if(world.getBlockTicks().hasScheduledTick(pos, this)) return state;
      if(powered) {
        world.scheduleTick(pos, this, 2);
      } else {
        lock_update = true;
        world.setBlock(pos, state.setValue(POWERED,false), 2|15);
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
    public BistableRelayBlock(long config, BlockBehaviour.Properties builder, AABB aabb)
    { super(config, builder, aabb); }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction redsrone_side)
    { return ((state.getValue(STATE) == 0) || (redsrone_side != getOutputFacing(state).getOpposite())) ? 0 : 15; }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, Random rnd)
    {}

    @Override
    public BlockState update(BlockState state, Level world, BlockPos pos, @Nullable BlockPos fromPos)
    {
      final boolean powered = isPowered(state, world, pos);
      final boolean pwstate = state.getValue(POWERED);
      if(powered == pwstate) return state;
      state = state.setValue(POWERED, powered);
      if(powered && !pwstate) {
        state = state.setValue(STATE, (state.getValue(STATE)==0) ? (1) : (0));
        world.setBlock(pos, state, 2|15);
        notifyOutputNeighbourOfStateChange(state, world, pos);
      } else if(!powered && pwstate) {
        world.setBlock(pos, state, 2|15);
      }
      return state;
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // PulseRelayBlock
  //--------------------------------------------------------------------------------------------------------------------

  public static class PulseRelayBlock extends RelayBlock
  {
    public PulseRelayBlock(long config, BlockBehaviour.Properties builder, AABB aabb)
    { super(config, builder, aabb); }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction redsrone_side)
    { return ((state.getValue(STATE) == 0) || (redsrone_side != getOutputFacing(state).getOpposite())) ? 0 : 15; }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, Random rnd)
    {
      if(state.getValue(STATE) == 0) return;
      state = state.setValue(STATE, 0);
      world.setBlock(pos, state, 2|15);
      notifyOutputNeighbourOfStateChange(state, world, pos);
    }

    @Override
    public BlockState update(BlockState state, Level world, BlockPos pos, @Nullable BlockPos fromPos)
    {
      final boolean powered = isPowered(state, world, pos);
      if(powered != state.getValue(POWERED)) {
        state = state.setValue(POWERED, powered);
        if(powered) {
          boolean trig = (state.getValue(STATE) == 0);
          state = state.setValue(STATE, 1);
          world.setBlock(pos, state, 2|15);
          if(trig) notifyOutputNeighbourOfStateChange(state, world, pos);
        } else {
          world.setBlock(pos, state, 2|15);
        }
      }
      if(!world.getBlockTicks().hasScheduledTick(pos, this)) {
        world.scheduleTick(pos, this, 2);
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

    public BridgeRelayBlock(long config, BlockBehaviour.Properties builder, AABB aabb)
    { super(config, builder, aabb); }

    protected int getInputPower(Level world, BlockPos relay_pos, Direction side)
    {
      final BlockPos pos = relay_pos.relative(side);
      final BlockState state = world.getBlockState(pos);
      int p = 0;
      if(power_update_recursion_level_ < 32) {
        ++power_update_recursion_level_;
        if(state.is(Blocks.REDSTONE_WIRE)) {
          p = Math.max(0, state.getValue(RedStoneWireBlock.POWER)-2);
        } else if(state.is(ModContent.TRACK_BLOCK)) {
          p = Math.max(0, RedstoneTrack.RedstoneTrackBlock.tile(world, pos).map((te->te.getRedstonePower(side, true))).orElse(0)-2);
        } else if(state.is(ModContent.BRIDGE_RELAY_BLOCK)) {
          if(state.getValue(FACING) != world.getBlockState(relay_pos).getValue(FACING)) {
            p = 0;
          } else if((state.getValue(ROTATION) & 0x1) != (world.getBlockState(relay_pos).getValue(ROTATION) & 0x1)) {
            p = 0;
          } else {
            p = getInputPower(world, pos, side);
          }
        } else {
          p = state.getSignal(world, pos, side);
          if((p<15) && (!state.isSignalSource()) && (state.shouldCheckWeakPower(world, pos, side))) {
            for(Direction d:Direction.values()) {
              if(d == side.getOpposite()) continue;
              p = Math.max(p, world.getBlockState(pos.relative(d)).getSignal(world, pos.relative(d), d));
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

    protected boolean isWireConnected(Level world, BlockPos relay_pos, Direction side)
    {
      final BlockPos pos = relay_pos.relative(side);
      final BlockState state = world.getBlockState(pos);
      return state.is(Blocks.REDSTONE_WIRE) || state.is(ModContent.TRACK_BLOCK);
    }

    protected boolean isSidePowered(Level world, BlockPos pos, Direction side)
    { return world.getSignal(pos.relative(side), side) > 0; }

    protected boolean isPowered(BlockState state, Level world, BlockPos pos)
    { return isSidePowered(world, pos, state.getValue(FACING)) || isSidePowered(world, pos, getOutputFacing(state).getOpposite()); }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction redstone_side)
    {
      if((redstone_side == getOutputFacing(state).getOpposite())) return state.getValue(POWERED) ? 15 : 0;
      int p = 0;
      final Direction left = getLeftFacing(state);
      final Direction right = getRightFacing(state);
      if(((redstone_side == left) || (redstone_side == right)) && (world instanceof ServerLevel)) {
        final boolean left_source = !isWireConnected((ServerLevel)world, pos, left) && world.getBlockState(pos.relative(left)).isSignalSource();
        final boolean right_source = !isWireConnected((ServerLevel)world, pos, right) && world.getBlockState(pos.relative(right)).isSignalSource();
        if(left_source && !right_source) {
          p = getInputPower((ServerLevel)world, pos, left);
        } else if(!left_source && right_source) {
          p = getInputPower((ServerLevel)world, pos, right);
        } else {
          p = Math.max(0, Math.max(getInputPower((ServerLevel)world, pos, left), getInputPower((ServerLevel)world, pos, right)));
        }
        //System.out.println("p@" + redstone_side.getOpposite() + ":" + p);
      }
      return p;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction redsrone_side)
    { return getSignal(state, world, pos, redsrone_side); }

    @Override
    public BlockState update(BlockState state, Level world, BlockPos pos, @Nullable BlockPos fromPos)
    {
      // Relay branch update
      {
        final boolean powered = isPowered(state, world, pos);
        if(powered != state.getValue(POWERED)) {
          if(!world.getBlockTicks().hasScheduledTick(pos, this)) {
            if(powered) {
              lock_update = true;
              world.setBlock(pos, (state=state.setValue(POWERED,true)), 2|15);
              world.neighborChanged(pos.relative(getOutputFacing(state)), this, pos);
              lock_update = false;
            } else {
              world.scheduleTick(pos, this, 2);
            }
          }
        }
      }
      // Wire branch update
      if(fromPos != null) {
        final Vec3i v = pos.subtract(fromPos);
        final Direction dir = Direction.getNearest(v.getX(), v.getY(), v.getZ());
        final Direction left = getLeftFacing(state);
        final Direction right = getRightFacing(state);
        if((dir == left) || (dir == right)) {
          lock_update = true;
          power_update_recursion_level_ = 0;
          int pr = getSignal(state, world, pos, right);
          int pl = getSignal(state, world, pos, left);
          final boolean track_powered = (pr>0) || (pl>0);
          //System.out.println("u->" + dir + " track_powered:" + track_powered);
          if(track_powered != (state.getValue(STATE)==1)) world.setBlock(pos, (state=state.setValue(STATE, track_powered?1:0)), 2|15);
          world.neighborChanged(pos.relative(dir), this, pos);
          lock_update = false;
        }
      }
      return state;
    }
  }
}
