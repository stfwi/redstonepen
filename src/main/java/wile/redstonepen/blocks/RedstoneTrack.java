/*
 * @file RedstoneTrack.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.blocks;

import com.google.common.collect.ImmutableMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.nbt.Tag;
import org.joml.Vector3f;
import wile.redstonepen.ModContent;
import wile.redstonepen.blocks.RedstoneTrack.defs.connections;
import wile.redstonepen.items.RedstonePenItem;
import wile.redstonepen.libmc.*;

import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class RedstoneTrack
{
  //--------------------------------------------------------------------------------------------------------------------
  // Definitions
  //--------------------------------------------------------------------------------------------------------------------

  public static final class defs
  {
    public static final long STATE_FLAG_WIR_MASK  = 0x0000000000ffffffL;
    public static final long STATE_FLAG_CON_MASK  = 0x000000003f000000L;
    public static final long STATE_FLAG_PWR_MASK  = 0x00ffffff00000000L;
    public static final int  STATE_FLAG_WIR_COUNT = 24;
    public static final int  STATE_FLAG_CON_COUNT = 6;
    public static final int  STATE_FLAG_WIR_POS   = 0;
    public static final int  STATE_FLAG_CON_POS   = 24;
    public static final int  STATE_FLAG_PWR_POS   = 32;

    public static final Direction[] REDSTONE_UPDATE_DIRECTIONS = new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH};

    public static final class connections
    {
      public static final Direction[] CONNECTION_BIT_ORDER  = {
        Direction.DOWN,Direction.UP, Direction.NORTH,Direction.SOUTH, Direction.EAST,Direction.WEST
      };

      // don't want extended enum for that small thing.
      public static final ImmutableMap<Direction,Integer> CONNECTION_BIT_ORDER_REV = new ImmutableMap.Builder<Direction,Integer>()
        .put(Direction.DOWN, 0)
        .put(Direction.UP, 1)
        .put(Direction.NORTH, 2)
        .put(Direction.SOUTH, 3)
        .put(Direction.EAST, 4)
        .put(Direction.WEST, 5)
        .build();

      public static final ImmutableMap<Long,Direction> BULK_FACE_MAPPING = new ImmutableMap.Builder<Long,Direction>()
        .put(0x0000000000000000L, Direction.DOWN)
        .put(0x0000000001000000L, Direction.DOWN)
        .put(0x0000000002000000L, Direction.UP)
        .put(0x0000000004000000L, Direction.NORTH)
        .put(0x0000000008000000L, Direction.SOUTH)
        .put(0x0000000010000000L, Direction.EAST)
        .put(0x0000000020000000L, Direction.WEST)
        .build();

      public static final ImmutableMap<Direction,Long> BULK_FACE_MAPPING_REV = new ImmutableMap.Builder<Direction,Long>()
        .put(Direction.DOWN,  0x0000000001000000L)
        .put(Direction.UP,    0x0000000002000000L)
        .put(Direction.NORTH, 0x0000000004000000L)
        .put(Direction.SOUTH, 0x0000000008000000L)
        .put(Direction.EAST,  0x0000000010000000L)
        .put(Direction.WEST,  0x0000000020000000L)
        .build();

      public static final ImmutableMap<Long, Tuple<Direction,Direction>> WIRE_FACE_DIRECTION_MAPPING = new ImmutableMap.Builder<Long,Tuple<Direction,Direction>>()
        .put(0x00000000L, new Tuple<>(Direction.DOWN,Direction.DOWN))
        .put(0x00000001L, new Tuple<>(Direction.DOWN,Direction.NORTH))
        .put(0x00000002L, new Tuple<>(Direction.DOWN,Direction.SOUTH))
        .put(0x00000004L, new Tuple<>(Direction.DOWN,Direction.EAST))
        .put(0x00000008L, new Tuple<>(Direction.DOWN,Direction.WEST))
        .put(0x00000010L, new Tuple<>(Direction.UP,Direction.NORTH))
        .put(0x00000020L, new Tuple<>(Direction.UP,Direction.SOUTH))
        .put(0x00000040L, new Tuple<>(Direction.UP,Direction.EAST))
        .put(0x00000080L, new Tuple<>(Direction.UP,Direction.WEST))
        .put(0x00000100L, new Tuple<>(Direction.NORTH,Direction.UP))
        .put(0x00000200L, new Tuple<>(Direction.NORTH,Direction.DOWN))
        .put(0x00000400L, new Tuple<>(Direction.NORTH,Direction.EAST))
        .put(0x00000800L, new Tuple<>(Direction.NORTH,Direction.WEST))
        .put(0x00001000L, new Tuple<>(Direction.SOUTH,Direction.UP))
        .put(0x00002000L, new Tuple<>(Direction.SOUTH,Direction.DOWN))
        .put(0x00004000L, new Tuple<>(Direction.SOUTH,Direction.EAST))
        .put(0x00008000L, new Tuple<>(Direction.SOUTH,Direction.WEST))
        .put(0x00010000L, new Tuple<>(Direction.EAST,Direction.UP))
        .put(0x00020000L, new Tuple<>(Direction.EAST,Direction.DOWN))
        .put(0x00040000L, new Tuple<>(Direction.EAST,Direction.NORTH))
        .put(0x00080000L, new Tuple<>(Direction.EAST,Direction.SOUTH))
        .put(0x00100000L, new Tuple<>(Direction.WEST,Direction.UP))
        .put(0x00200000L, new Tuple<>(Direction.WEST,Direction.DOWN))
        .put(0x00400000L, new Tuple<>(Direction.WEST,Direction.NORTH))
        .put(0x00800000L, new Tuple<>(Direction.WEST,Direction.SOUTH))
        .build();

      public static final ImmutableMap<Long,Tuple<Direction,Direction>> INTERNAL_EDGE_CONNECTION_MAPPING = new ImmutableMap.Builder<Long,Tuple<Direction,Direction>>()
        .put(0x00000001L|0x00000200L, new Tuple<>(Direction.DOWN,Direction.NORTH))
        .put(0x00000002L|0x00002000L, new Tuple<>(Direction.DOWN,Direction.SOUTH))
        .put(0x00000004L|0x00020000L, new Tuple<>(Direction.DOWN,Direction.EAST))
        .put(0x00000008L|0x00200000L, new Tuple<>(Direction.DOWN,Direction.WEST))
        .put(0x00000010L|0x00000100L, new Tuple<>(Direction.UP,Direction.NORTH))
        .put(0x00000020L|0x00001000L, new Tuple<>(Direction.UP,Direction.SOUTH))
        .put(0x00000040L|0x00010000L, new Tuple<>(Direction.UP,Direction.EAST))
        .put(0x00000080L|0x00100000L, new Tuple<>(Direction.UP,Direction.WEST))
        .put(0x00000400L|0x00040000L, new Tuple<>(Direction.NORTH,Direction.EAST))
        .put(0x00000800L|0x00400000L, new Tuple<>(Direction.NORTH,Direction.WEST))
        .put(0x00004000L|0x00080000L, new Tuple<>(Direction.SOUTH,Direction.EAST))
        .put(0x00008000L|0x00800000L, new Tuple<>(Direction.SOUTH,Direction.WEST))
        .build();

      // -- bit mapping access -------------------------------------------------------------------------------------

      /**
       * Returns the state bit for a connector on a specific face.
       */
      public static long getBulkConnectorBit(Direction face)
      { return connections.BULK_FACE_MAPPING_REV.get(face); }

      /**
       * Returns the state bit for a wire (with direction `wire_direction`)
       * on a specific face.
       */
      public static long getWireBit(Direction face, Direction wire_direction)
      {
        return connections.WIRE_FACE_DIRECTION_MAPPING.entrySet().stream()
          .filter(kv->kv.getValue().getA()==face && kv.getValue().getB()==wire_direction)
          .findFirst()
          .map(Map.Entry::getKey).orElse(0L);
      }

      public static Tuple<Direction,Direction> getWireBitSideAndDirection(long wirebit)
      { return WIRE_FACE_DIRECTION_MAPPING.getOrDefault(wirebit, new Tuple<>(Direction.DOWN,Direction.DOWN)); }

      public static List<Direction> getVanillaWireConnectionDirections(long mask)
      {
        if((mask & 0x0000000fL)==0) return Collections.emptyList();
        final List<Direction> r = new ArrayList<>(4);
        if((mask & 0x00000001L) != 0) r.add(Direction.NORTH);
        if((mask & 0x00000002L) != 0) r.add(Direction.SOUTH);
        if((mask & 0x00000004L) != 0) r.add(Direction.EAST);
        if((mask & 0x00000008L) != 0) r.add(Direction.WEST);
        return r;
      }

      public static boolean hasVanillaWireConnection(long mask, Direction side)
      {
        return switch (side) {
          case NORTH -> ((mask & 0x00000001L) != 0);
          case SOUTH -> ((mask & 0x00000002L) != 0);
          case EAST -> ((mask & 0x00000004L) != 0);
          case WEST -> ((mask & 0x00000008L) != 0);
          default -> false;
        };
      }

      public static boolean hasBulkConnection(long mask, Direction side)
      { return ((connections.BULK_FACE_MAPPING_REV.get(side) & mask) != 0); }

      public static boolean hasRedstoneConnection(long mask, Direction side)
      {
        return switch (side) {
          case DOWN -> ((mask & 0x01222200L) != 0);
          case UP -> ((mask & 0x02111100L) != 0);
          case NORTH -> ((mask & 0x04440011L) != 0);
          case SOUTH -> ((mask & 0x08880022L) != 0);
          case EAST -> ((mask & 0x10004444L) != 0);
          case WEST -> ((mask & 0x20008888L) != 0);
        };
      }

      public static long getWireElementsOnFace(Direction face)
      { return (0xfL<<((connections.CONNECTION_BIT_ORDER_REV.get(face)*4)+STATE_FLAG_WIR_POS)); }

      public static long getAllElementsOnFace(Direction face)
      {
        final int index = connections.CONNECTION_BIT_ORDER_REV.get(face);
        return (0xfL<<((index*4)+STATE_FLAG_WIR_POS))|(0x1L<<(index+STATE_FLAG_CON_POS));
      }

    }

    public static class shape
    {
      private static final double SHAPE_LAYER_THICKNESS = 0.01;
      private static final double SHAPE_TRACK_HALFWIDTH = 1;

      private static final VoxelShape DOWN_SHAPE = Auxiliaries.getUnionShape(
        Auxiliaries.getPixeledAABB(8-SHAPE_TRACK_HALFWIDTH,0,0, 8+SHAPE_TRACK_HALFWIDTH,SHAPE_LAYER_THICKNESS,16),
        Auxiliaries.getPixeledAABB(0,0,8-SHAPE_TRACK_HALFWIDTH,16,SHAPE_LAYER_THICKNESS, 8+SHAPE_TRACK_HALFWIDTH)
      );
      private static final VoxelShape UP_SHAPE = Auxiliaries.getUnionShape(
        Auxiliaries.getPixeledAABB(8-SHAPE_TRACK_HALFWIDTH,16-SHAPE_LAYER_THICKNESS,0, 8+SHAPE_TRACK_HALFWIDTH,16,16),
        Auxiliaries.getPixeledAABB(0,16-SHAPE_LAYER_THICKNESS,8-SHAPE_TRACK_HALFWIDTH,16,16, 8+SHAPE_TRACK_HALFWIDTH)
      );
      private static final VoxelShape WEST_SHAPE = Auxiliaries.getUnionShape(
        Auxiliaries.getPixeledAABB(0,0,8-SHAPE_TRACK_HALFWIDTH, SHAPE_LAYER_THICKNESS,16, 8+SHAPE_TRACK_HALFWIDTH),
        Auxiliaries.getPixeledAABB(0,8-SHAPE_TRACK_HALFWIDTH,0, SHAPE_LAYER_THICKNESS, 8+SHAPE_TRACK_HALFWIDTH,16)
      );
      private static final VoxelShape EAST_SHAPE = Auxiliaries.getUnionShape(
        Auxiliaries.getPixeledAABB(16-SHAPE_LAYER_THICKNESS,0,8-SHAPE_TRACK_HALFWIDTH, 16,16, 8+SHAPE_TRACK_HALFWIDTH),
        Auxiliaries.getPixeledAABB(16-SHAPE_LAYER_THICKNESS,8-SHAPE_TRACK_HALFWIDTH,0, 16, 8+SHAPE_TRACK_HALFWIDTH,16)
      );

      private static final VoxelShape NORTH_SHAPE = Auxiliaries.getUnionShape(
        Auxiliaries.getPixeledAABB(0,8-SHAPE_TRACK_HALFWIDTH,0, 16, 8+SHAPE_TRACK_HALFWIDTH,SHAPE_LAYER_THICKNESS),
        Auxiliaries.getPixeledAABB(8-SHAPE_TRACK_HALFWIDTH,0,0,  8+SHAPE_TRACK_HALFWIDTH,16,SHAPE_LAYER_THICKNESS)
      );
      private static final VoxelShape SOUTH_SHAPE = Auxiliaries.getUnionShape(
        Auxiliaries.getPixeledAABB(0,8-SHAPE_TRACK_HALFWIDTH,16-SHAPE_LAYER_THICKNESS, 16, 8+SHAPE_TRACK_HALFWIDTH,16),
        Auxiliaries.getPixeledAABB(8-SHAPE_TRACK_HALFWIDTH,0,16-SHAPE_LAYER_THICKNESS,  8+SHAPE_TRACK_HALFWIDTH,16,16)
      );

      // maps are too slow, 64 objects are ok to pre-allocate.
      // check if thread sync needed.
      private static final VoxelShape[] shape_cache = new VoxelShape[64];

      public static VoxelShape get(int faces)
      {
        if(shape_cache[faces] == null) {
          VoxelShape shape = Shapes.empty();
          if((faces & 0x01)!=0) shape = Shapes.join(shape, DOWN_SHAPE, BooleanOp.OR);
          if((faces & 0x02)!=0) shape = Shapes.join(shape, UP_SHAPE, BooleanOp.OR);
          if((faces & 0x04)!=0) shape = Shapes.join(shape, NORTH_SHAPE, BooleanOp.OR);
          if((faces & 0x08)!=0) shape = Shapes.join(shape, SOUTH_SHAPE, BooleanOp.OR);
          if((faces & 0x10)!=0) shape = Shapes.join(shape, EAST_SHAPE, BooleanOp.OR);
          if((faces & 0x20)!=0) shape = Shapes.join(shape, WEST_SHAPE, BooleanOp.OR);
          shape_cache[faces] = shape;
        }
        return shape_cache[faces];
      }
    }

    public static final class models
    {
      public static final ImmutableMap<Long,String> STATE_WIRE_MAPPING = new ImmutableMap.Builder<Long,String>()
        .put(0x00000000L, "none")
        .put(0x00000001L, "dn")
        .put(0x00000002L, "ds")
        .put(0x00000004L, "de")
        .put(0x00000008L, "dw")
        .put(0x00000010L, "un")
        .put(0x00000020L, "us")
        .put(0x00000040L, "ue")
        .put(0x00000080L, "uw")
        .put(0x00000100L, "nu")
        .put(0x00000200L, "nd")
        .put(0x00000400L, "ne")
        .put(0x00000800L, "nw")
        .put(0x00001000L, "su")
        .put(0x00002000L, "sd")
        .put(0x00004000L, "se")
        .put(0x00008000L, "sw")
        .put(0x00010000L, "eu")
        .put(0x00020000L, "ed")
        .put(0x00040000L, "en")
        .put(0x00080000L, "es")
        .put(0x00100000L, "wu")
        .put(0x00200000L, "wd")
        .put(0x00400000L, "wn")
        .put(0x00800000L, "ws")
        .build();

      public static final ImmutableMap<Long,String> STATE_CONNECT_MAPPING = new ImmutableMap.Builder<Long,String>()
        .put(0x0000000000000000L, "none")
        .put(0x0000000001000000L, "dc")
        .put(0x0000000002000000L, "uc")
        .put(0x0000000004000000L, "nc")
        .put(0x0000000008000000L, "sc")
        .put(0x0000000010000000L, "ec")
        .put(0x0000000020000000L, "wc")
        .build();

      public static final ImmutableMap<Long,String> STATE_CNTWIRE_MAPPING = new ImmutableMap.Builder<Long,String>()
        .put(0x0000000000000000L, "none")
        .put(0x0000000001000000L, "dm")
        .put(0x0000000002000000L, "um")
        .put(0x0000000004000000L, "nm")
        .put(0x0000000008000000L, "sm")
        .put(0x0000000010000000L, "em")
        .put(0x0000000020000000L, "wm")
        .build();
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Block
  //--------------------------------------------------------------------------------------------------------------------

  public static class RedstoneTrackBlock extends StandardBlocks.WaterLoggable implements EntityBlock
  {
    public RedstoneTrackBlock(long config, BlockBehaviour.Properties builder)
    { super(config, builder.pushReaction(PushReaction.DESTROY)); }

    public static Optional<TrackBlockEntity> tile(BlockGetter world, BlockPos pos)
    { final BlockEntity te=world.getBlockEntity(pos); return (((te instanceof TrackBlockEntity) && (!te.isRemoved())) ? Optional.of((TrackBlockEntity)te) : Optional.empty()); }

    public static boolean canBePlacedOnFace(BlockState state, Level world, BlockPos pos, Direction face)
    {
      if(state.getBlock() instanceof PistonBaseBlock) {
        Direction pface = state.getValue(PistonBaseBlock.FACING);
        return (face != pface);
      }
      if(state.getBlock() instanceof MovingPistonBlock) return true;
      if(state.is(Blocks.HOPPER)) return (face == Direction.UP);
      return state.isFaceSturdy(world, pos, face);
    }

    private boolean can_provide_power_ = true;

    //------------------------------------------------------------------------------------------------------------------

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    { super.createBlockStateDefinition(builder); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    { return new RedstoneTrack.TrackBlockEntity(pos, state); }

    @Override
    public boolean hasDynamicDropList()
    { return true; }

    @Override
    public List<ItemStack> dropList(BlockState state, Level world, @Nullable BlockEntity te, boolean explosion)
    {
      if(!(te instanceof TrackBlockEntity)) return Collections.emptyList();
      int num_connections = ((TrackBlockEntity)te).getRedstoneDustCount();
      if(num_connections <= 0) return Collections.emptyList();
      return Collections.singletonList(new ItemStack(Items.REDSTONE, num_connections));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context)
    { return context.getLevel().getBlockState(context.getClickedPos()).canBeReplaced(context) ? super.getStateForPlacement(context) : null; }

    @Override
    public Item asItem()
    { return Items.REDSTONE; }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type)
    { return true; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
      final int wires = tile(world, pos).map(TrackBlockEntity::getWireFlags).orElse(0);
      final int faces = (((wires & 0x00000f) != 0) ? 0x01 : 0)
        | (((wires & 0x0000f0) != 0) ? 0x02 : 0)
        | (((wires & 0x000f00) != 0) ? 0x04 : 0)
        | (((wires & 0x00f000) != 0) ? 0x08 : 0)
        | (((wires & 0x0f0000) != 0) ? 0x10 : 0)
        | (((wires & 0xf00000) != 0) ? 0x20 : 0);
      return defs.shape.get(faces);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    { return Shapes.empty(); }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos)
    { return !state.getValue(WATERLOGGED); }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state)
    { return true; }

    @Deprecated
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos)
    { return 0; }

    @Deprecated
    public RenderShape getRenderShape(BlockState state)
    { return RenderShape.ENTITYBLOCK_ANIMATED; }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos)
    { return true; }

    @Deprecated
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, @Nullable Direction side)
    {
      // @todo: fabric: not available, need to hook into `RedstoneWireBlock.shouldConnectTo(BlockState blockState, @Nullable Direction direction)` or leave it as it is.
      return (side != null) && (tile(world,pos).map(te->te.hasVanillaRedstoneConnection(side.getOpposite()))).orElse(false);
    }

    @Override
    public boolean isSignalSource(BlockState state)
    { return can_provide_power_; }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state)
    { return false; }

    public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos)
    { return 0; }

    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction redstone_side)
    { return can_provide_power_ ? tile(world, pos).map(te->te.getRedstonePower(redstone_side, true)).orElse(0) : 0; }

    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction redstone_side)
    { return can_provide_power_ ? tile(world, pos).map(te->te.getRedstonePower(redstone_side, false)).orElse(0) : 0; }

    @Override
    public boolean shouldCheckWeakPower(BlockState state, LevelReader level, BlockPos pos, Direction side)
    { return false; }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource rnd)
    { if(!tile(world,pos).map(te->te.sync(false)).orElse(false)) world.removeBlock(pos, false); }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor world, BlockPos pos, BlockPos facingPos)
    {
      if(!world.isClientSide()) {
        if(tile(world, pos).map(te->te.handlePostPlacement(facing, facingState, facingPos)).orElse(true)) {
          world.scheduleTick(pos, this, 1);
        } else {
          world.removeBlock(pos, false);
        }
      }
      return super.updateShape(state, facing, facingState, world, pos, facingPos);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving)
    {}

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving)
    {
      if(isMoving || state.is(newState.getBlock())) return;
      super.onRemove(state, world, pos, newState, isMoving);
      if(world.isClientSide()) return;
      notifyAdjacent(world, pos);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult rtr)
    {
      if(player.getItemInHand(hand).is(Items.DEBUG_STICK)) {
        if(world.isClientSide) return InteractionResult.SUCCESS;
        if(world.getBlockEntity(pos) instanceof TrackBlockEntity te) te.toggle_trace(player);
        return InteractionResult.CONSUME;
      } else {
        return onBlockActivated(state, world, pos, player, hand, rtr, false);
      }
    }

    public InteractionResult onBlockActivated(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult rtr, boolean remove_only)
    {
      {
        ItemStack stack = player.getItemInHand(hand);
        if((!stack.isEmpty()) && (stack.getItem()!=Items.REDSTONE) && (!RedstonePenItem.isPen(stack))) {
          BlockPos behind_pos = pos.relative(rtr.getDirection());
          BlockState behind_state = world.getBlockState(behind_pos);
          if(behind_state.isRedstoneConductor(world, behind_pos)) {
            return behind_state.getBlock().use(behind_state,world,behind_pos, player, hand, rtr);
          }
          return InteractionResult.PASS;
        }
      }
      if(world.isClientSide()) return InteractionResult.SUCCESS;
      if(!RedstonePenItem.hasEnoughRedstone(player.getItemInHand(hand), 1, player)) remove_only = true;
      TrackBlockEntity te = tile(world, pos).orElse(null);
      if(te==null) return InteractionResult.FAIL;
      int redstone_use = te.handleActivation(pos, player, hand, rtr.getDirection(), rtr.getLocation(), remove_only);
      if(redstone_use == 0) {
        return InteractionResult.PASS;
      } else if(redstone_use < 0) {
        RedstonePenItem.pushRedstone(player.getItemInHand(hand), -redstone_use, player);
        if(te.getWireFlags() == 0) {
          world.setBlock(pos, state.getFluidState().createLegacyBlock(), 1|2);
        } else {
          final Map<BlockPos,BlockPos> blocks_to_update = te.updateAllPowerValuesFromAdjacent();
          for(Map.Entry<BlockPos,BlockPos> update_pos:blocks_to_update.entrySet()) {
            world.neighborChanged(update_pos.getKey(), this, update_pos.getValue());
          }
        }
        world.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.4f, 2f);
      } else {
        RedstonePenItem.popRedstone(player.getItemInHand(hand), redstone_use, player, hand);
        world.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.4f, 2.4f);
      }
      updateNeighbourShapes(state, world, pos);
      notifyAdjacent(world, pos);
      return InteractionResult.CONSUME;
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block fromBlock, BlockPos fromPos, boolean isMoving)
    {
      if(world.isClientSide()) return;
      final Map<BlockPos,BlockPos> blocks_to_update = tile(world, pos).map(te->te.handleNeighborChanged(fromPos)).orElse(Collections.emptyMap());
      if(blocks_to_update.isEmpty()) return;
      try {
        for(Map.Entry<BlockPos,BlockPos> update_pos:blocks_to_update.entrySet()) {
          world.neighborChanged(update_pos.getKey(), this, update_pos.getValue());
        }
      } catch(Throwable ex) {
        Auxiliaries.logError("Track neighborChanged recursion detected, dropping!");
        final int num_redstone = tile(world, pos).map(TrackBlockEntity::getRedstoneDustCount).orElse(0);
        if(num_redstone > 0) {
          Vec3 p = Vec3.atCenterOf(pos);
          world.addFreshEntity(new ItemEntity(world, p.x, p.y, p.z, new ItemStack(Items.REDSTONE, num_redstone)));
          world.setBlock(pos, world.getBlockState(pos).getFluidState().createLegacyBlock(), 2|16);
        }
      }
    }

    @Override
    public void updateIndirectNeighbourShapes(BlockState state, LevelAccessor worldIn, BlockPos pos, int flags, int recursionLeft)
    {}

    @Environment(EnvType.CLIENT)
    private void spawnPoweredParticle(Level world, RandomSource rand, BlockPos pos, Vec3 color, Direction from, Direction to, float minChance, float maxChance) {
      float f = maxChance - minChance;
      if(rand.nextFloat() < 0.3f * f) {
        double c1 = 0.4375;
        double c2 = minChance + f * rand.nextFloat();
        double p0 = 0.5 + (c1 * from.getStepX()) + (c2*.4 * to.getStepX());
        double p1 = 0.5 + (c1 * from.getStepY()) + (c2*.4 * to.getStepY());
        double p2 = 0.5 + (c1 * from.getStepZ()) + (c2*.4 * to.getStepZ());
        world.addParticle(new DustParticleOptions(new Vector3f(color.toVector3f()),1.0F), pos.getX()+p0, pos.getY()+p1, pos.getZ()+p2, 0, 0., 0);
      }
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource rand)
    {
      if(rand.nextFloat() > 0.4) return;
      final TrackBlockEntity te = tile(world,pos).orElse(null);
      if((te == null) || ((te.getStateFlags() & defs.STATE_FLAG_PWR_MASK) == 0)) return;
      final Vec3 color = new Vec3(0.6f,0,0);
      for(Direction side: Direction.values()) {
        int p = te.getSidePower(side);
        if(p == 0) continue;
        spawnPoweredParticle(world, rand, pos, color, side, side.getOpposite(), -0.5F, 0.5F);
      }
    }

    //------------------------------------------------------------------------------------------------------------------

    public void checkSmartPlacement(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult rtr)
    {
      if(world.isClientSide()) return;
      final ItemStack pen = player.getItemInHand(hand);
      if(!RedstonePenItem.hasEnoughRedstone(pen, 2, player)) return;
      final TrackBlockEntity te = tile(world,pos).orElse(null);
      if(te == null) return;
      final Tuple<Direction,Direction> side_dir = defs.connections.getWireBitSideAndDirection(te.getWireFlags());
      final Direction face = side_dir.getA();
      final Direction dir = side_dir.getB();
      if((face==Direction.DOWN) && (dir==Direction.DOWN)) return; // more than one wire flag set.
      int num_placed = 0;
      long flags_to_add = 0;
      for(Direction d: Direction.values()) {
        if(!RedstonePenItem.hasEnoughRedstone(pen, num_placed, player)) return;
        if((d==face) || (d==face.getOpposite()) || (d==dir)) continue;
        final BlockState ostate = world.getBlockState(pos.relative(d));
        if(ostate.is(this)) {
          final TrackBlockEntity ote = tile(world, pos.relative(d)).orElse(null);
          if(ote != null) {
            final int oflags = ote.getWireFlags();
            if((defs.connections.getWireBit(face, d.getOpposite()) & oflags) != 0) {
              flags_to_add |= connections.getWireBit(face, d);
              ++num_placed;
            }
          }
        }
      }
      if(num_placed == 0) {
        final Direction odir = dir.getOpposite();
        final BlockPos opos = pos.relative(odir);
        final BlockState ostate = world.getBlockState(opos);
        if(!RsSignals.hasSignalConnector(ostate, world, opos, dir)) {
          flags_to_add |= connections.getWireBit(face, odir);
          ++num_placed;
        }
      }
      if(num_placed > 0) {
        int n_added = te.addWireFlags(flags_to_add);
        te.sync(true);
        RedstonePenItem.popRedstone(pen, n_added, player, hand);
        te.updateConnections(2);
        te.updateAllPowerValuesFromAdjacent();
        updateNeighbourShapes(state, world, pos);
        notifyAdjacent(world, pos);
      }
    }

    private void disablePower(boolean disable)
    { can_provide_power_ = !disable; }

    private void updateNeighbourShapes(final BlockState state, final Level world, final BlockPos pos)
    { state.updateNeighbourShapes(world, pos, 1|2); }

    public void notifyAdjacent(final Level world, final BlockPos pos)
    {
      world.updateNeighborsAt(pos, this);
      for(Direction dir0: BlockBehaviour.UPDATE_SHAPE_ORDER) {
        BlockPos ppos = pos.relative(dir0);
        world.updateNeighborsAtExceptFromFacing(ppos, world.getBlockState(ppos).getBlock(), dir0.getOpposite());
        for(Direction dir1: BlockBehaviour.UPDATE_SHAPE_ORDER) {
          if(dir0 == dir1.getOpposite()) return;
          ppos = pos.relative(dir0).relative(dir1);
          if(ppos == pos) continue;
          final BlockState diagonal_state = world.getBlockState(ppos);
          if(diagonal_state.getBlock() != this) continue;
          world.neighborChanged(ppos, this, pos);
        }
      }
    }

  }

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entity
  //--------------------------------------------------------------------------------------------------------------------

  public static class TrackBlockEntity extends StandardEntityBlocks.StandardBlockEntity implements Networking.IPacketTileNotifyReceiver
  {
    public static class TrackNet
    {
      public final List<BlockPos> neighbour_positions;
      public final List<Direction> neighbour_sides;
      public final List<Direction> internal_sides;
      public final List<Direction> power_sides;
      public int power;

      public TrackNet(List<BlockPos> positions, List<Direction> ext_sides, List<Direction> int_sides, List<Direction> pwr_sides)
      { neighbour_positions =positions; neighbour_sides =ext_sides; internal_sides=int_sides; power_sides=pwr_sides; power = 0; }

      public TrackNet(List<BlockPos> positions, List<Direction> ext_sides, List<Direction> int_sides, List<Direction> pwr_sides, int power_setval)
      { neighbour_positions =positions; neighbour_sides =ext_sides; internal_sides=int_sides; power_sides=pwr_sides; power = power_setval; }
    }

    private long state_flags_ = 0;      // server/client
    private final List<TrackNet> nets_ = new ArrayList<>();
    private final Block[] block_change_tracking_ = {Blocks.AIR,Blocks.AIR,Blocks.AIR,Blocks.AIR,Blocks.AIR,Blocks.AIR};
    private boolean trace_ = false;

    public TrackBlockEntity(BlockPos pos, BlockState state)
    { super(Registries.getBlockEntityTypeOfBlock(state.getBlock()), pos, state); }

    public CompoundTag readnbt(CompoundTag nbt)
    {
      state_flags_ = nbt.getLong("sflags");
      nets_.clear();
      if(nbt.contains("nets", Tag.TAG_LIST)) {
        final ListTag lst = nbt.getList("nets", Tag.TAG_COMPOUND);
        try {
          for(int i=0; i<lst.size(); ++i) {
            CompoundTag route_nbt = lst.getCompound(i);
            nets_.add(new TrackNet(
              Arrays.stream(route_nbt.getLongArray("npos")).mapToObj(BlockPos::of).collect(Collectors.toList()),
              Arrays.stream(route_nbt.getIntArray("nsid")).mapToObj(Direction::from3DDataValue).collect(Collectors.toList()),
              Arrays.stream(route_nbt.getIntArray("ifac")).mapToObj(Direction::from3DDataValue).collect(Collectors.toList()),
              Arrays.stream(route_nbt.getIntArray("pfac")).mapToObj(Direction::from3DDataValue).collect(Collectors.toList()),
              route_nbt.getInt("power")
            ));
          }
        } catch(Throwable ex) {
          nets_.clear();
          Auxiliaries.logError("Dropped invalid NBT for Redstone Track at pos " + getBlockPos());
        }
      }
      return nbt;
    }

    private CompoundTag writenbt(CompoundTag nbt)
    { return writenbt(nbt, false); }

    private CompoundTag writenbt(CompoundTag nbt, boolean sync_packet)
    {
      nbt.putLong("sflags", state_flags_);
      if(sync_packet) return nbt;
      if(!nets_.isEmpty()) {
        final ListTag lst = new ListTag();
        for(TrackNet net: nets_) {
          CompoundTag route_nbt = new CompoundTag();
          route_nbt.putInt("power", net.power);
          route_nbt.put("npos", new LongArrayTag(net.neighbour_positions.stream().map(BlockPos::asLong).collect(Collectors.toList())));
          route_nbt.put("nsid", new IntArrayTag(net.neighbour_sides.stream().map(Direction::get3DDataValue).collect(Collectors.toList())));
          route_nbt.put("ifac", new IntArrayTag(net.internal_sides.stream().map(Direction::get3DDataValue).collect(Collectors.toList())));
          route_nbt.put("pfac", new IntArrayTag(net.power_sides.stream().map(Direction::get3DDataValue).collect(Collectors.toList())));
          lst.add(route_nbt);
        }
        nbt.put("nets", lst);
      }
      return nbt;
    }

    @Override
    public void load(CompoundTag nbt)
    { super.load(nbt); readnbt(nbt); }

    @Override
    protected void saveAdditional(CompoundTag nbt)
    { super.saveAdditional(nbt); writenbt(nbt); }

    @Override
    public CompoundTag getUpdateTag()
    { CompoundTag nbt = super.getUpdateTag(); writenbt(nbt, true); return nbt; }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) // on client
    { readnbt(pkt.getTag()); super.onDataPacket(net, pkt); }

    @Override
    public void handleUpdateTag(CompoundTag tag) // on client
    { load(tag); }

    @Override
    public void onServerPacketReceived(CompoundTag nbt)
    { readnbt(nbt); }

    @Override
    public void onClientPacketReceived(Player player, CompoundTag nbt)
    {}

    @Environment(EnvType.CLIENT)
    public double getViewDistance()
    { return 64; }

    /// -------------------------------------------------------------------------------------------

    public boolean sync(boolean schedule)
    {
      if(level.isClientSide()) return true;
      setChanged();
      if(schedule && (!getLevel().getBlockTicks().hasScheduledTick(getBlockPos(), ModContent.references.TRACK_BLOCK))) {
        getLevel().scheduleTick(getBlockPos(), ModContent.references.TRACK_BLOCK, 1);
      } else {
        Networking.PacketTileNotifyServerToClient.sendToPlayers(this, writenbt(new CompoundTag(), true));
      }
      return true;
    }

    public long getStateFlags()
    { return state_flags_; }

    public int addWireFlags(long flags)
    {
      int n_added = 0;
      for(int i=0; i<getWireFlagCount(); ++i) {
        long mask = 1L<<i;
        if(((flags & mask)!=0) && ((state_flags_ & mask))==0) {
          state_flags_ |= mask;
          ++n_added;
        }
      }
      return n_added;
    }

    public int getWireFlags()
    { return (int)((state_flags_ & defs.STATE_FLAG_WIR_MASK)>>defs.STATE_FLAG_WIR_POS); }

    public boolean getWireFlag(int index)
    { return (state_flags_ & (1L<<(defs.STATE_FLAG_WIR_POS+index))) != 0; }

    public int getWireFlagCount()
    { return defs.STATE_FLAG_WIR_COUNT; }

    public int getConnectionFlags()
    { return (int)((state_flags_ & defs.STATE_FLAG_CON_MASK)>>defs.STATE_FLAG_CON_POS); }

    public boolean getConnectionFlag(int index)
    { return (state_flags_ & (1L<<(defs.STATE_FLAG_CON_POS+index))) != 0; }

    public int getConnectionFlagCount()
    { return defs.STATE_FLAG_CON_COUNT; }

    public int getSidePower(Direction side)
    {
      final int shift = defs.STATE_FLAG_PWR_POS + 4*connections.CONNECTION_BIT_ORDER_REV.getOrDefault(side, 0);
      return (int)((state_flags_>>shift) & 0xf);
    }

    public void setSidePower(Direction side, int p)
    {
      final int shift = defs.STATE_FLAG_PWR_POS + 4*connections.CONNECTION_BIT_ORDER_REV.getOrDefault(side, 0);
      state_flags_ = (state_flags_ & ~(((long)(0xf))<<shift)) | (((long)(p & 0xf))<<shift);
    }

    public boolean hasVanillaRedstoneConnection(Direction side)
    { return defs.connections.hasVanillaWireConnection(getStateFlags(), side) || ((state_flags_ & defs.connections.getBulkConnectorBit(side))!=0); }

    public int getRedstonePower(Direction redstone_side, boolean weak)
    {
      final Direction own_side = redstone_side.getOpposite();
      int p = 0;
      for(TrackNet net:nets_) {
        if(!net.power_sides.contains(own_side)) continue;
        p = Math.max(p, net.power);
        if(p >= 15) break;
      }
      p = ((p <= 0) || (!(getLevel().getBlockState(getBlockPos().relative(own_side)).is(Blocks.REDSTONE_WIRE)))) ? p : (p-1);
      if(trace_) Auxiliaries.logWarn(String.format("POWR: %s @%s==%d", posstr(getBlockPos()), redstone_side, p));
      return p;
    }

    public int getRedstoneDustCount()
    {
      int n = 0;
      {
        int rem = getWireFlags();
        for(int i=0; (rem!=0) && (i<defs.STATE_FLAG_WIR_COUNT); ++i) {
          if((rem & 1L) != 0) ++n;
          rem >>= 1;
        }
      }
      {
        int rem = getConnectionFlags();
        for(int i=0; (rem!=0) && (i<defs.STATE_FLAG_CON_COUNT); ++i) {
          if((rem & 1L) != 0) ++n;
          rem >>= 1;
        }
      }
      return n;
    }

    public void toggle_trace(@Nullable Player player)
    { trace_ = !trace_; if(player!=null) Auxiliaries.playerChatMessage(player, "Trace: " + trace_); }

    public int handleActivation(BlockPos pos, Player player, InteractionHand hand, Direction clicked_face, Vec3 hitvec, boolean remove_only)
    {
      final ItemStack used_stack = player.getItemInHand(hand);
      if((!used_stack.isEmpty()) && (used_stack.getItem()!=Items.REDSTONE) && (!RedstonePenItem.isPen(used_stack))) return 0;
      long flip_mask;
      final Direction face = clicked_face.getOpposite();
      {
        final Vec3 hit_r = hitvec.subtract(Vec3.atCenterOf(pos));
        Vec3 hit = switch (clicked_face) {
          case WEST, EAST   -> hit_r.multiply(0, 1, 1);
          case SOUTH, NORTH -> hit_r.multiply(1, 1, 0);
          default           -> hit_r.multiply(1, 0, 1);
        };
        final Direction dir = Direction.getNearest(hit.x(), hit.y(), hit.z());
        if((hit.length() < 0.12) && ((!remove_only) || (getConnectionFlags()!=0))) {
          // Centre connection
          if((getWireFlags() & defs.connections.getAllElementsOnFace(face))!=0) {
            flip_mask = defs.connections.getBulkConnectorBit(face);
          } else {
            flip_mask = defs.connections.getWireBit(face, dir);
          }
        } else {
          // Wire
          flip_mask = defs.connections.getWireBit(face, dir);
        }
      }
      // Explicit assignment not just `state_flags_ ^ flip_mask`.
      int material_use = 0;
      {
        if((state_flags_ & flip_mask) != 0) {
          state_flags_ &= ~flip_mask;
          material_use -= 1;
          final long bc = defs.connections.getBulkConnectorBit(face);
          if((state_flags_ & defs.connections.getAllElementsOnFace(face)) == bc) {
            state_flags_ &= ~bc;
            material_use -= 1;
          }
          if(getWireFlags()==0) {
            material_use -= getRedstoneDustCount();
            state_flags_ = 0;
          }
        } else if(!used_stack.isEmpty() && (!remove_only)) {
          boolean can_place = false;
          for(Direction side: Direction.values()) {
            if((defs.connections.getAllElementsOnFace(side) & flip_mask) == 0) continue;
            can_place = true;
            break;
          }
          if(can_place) {
            state_flags_ |= flip_mask;
            material_use += 1;
          }
        }
      }
      if(material_use != 0) {
        // Selecively update power of internal tracks and external connected blocks.
        List<BlockPos> connected, disconnected;
        final int initial_side_power = getSidePower(face);
        {
          // updateConnections() covers about all situations for internal power track updates
          // and external notification required, except for bulk connections, where external wire
          // connections are hidden due to the "around-upate" of the bulk connector. In this case
          // the net list for the corresponding side is inspected separately to add the changed
          // external wire connections.
          setSidePower(face,0);
          final Map<BlockPos,BlockPos> change_notifications_before = updateAllPowerValuesFromAdjacent();
          final Set<BlockPos> net_neighbours_before = nets_.stream().filter(net->net.internal_sides.contains(face)).map(net->net.neighbour_positions).findFirst().map(HashSet::new).orElse(new HashSet<>());
          updateConnections(1);
          setSidePower(face,0);
          final Map<BlockPos,BlockPos> change_notifications_after = updateAllPowerValuesFromAdjacent();
          disconnected = change_notifications_before.keySet().stream().filter(p -> !change_notifications_after.containsKey(p)).collect(Collectors.toList());
          connected = change_notifications_after.keySet().stream().filter((p) -> !change_notifications_before.containsKey(p)).collect(Collectors.toList());
          if(connected.isEmpty() && disconnected.isEmpty() && defs.connections.hasBulkConnection(getStateFlags(), face)) {
            // Bulk may update everything around, hiding external tracks that need updating.
            final Set<BlockPos> net_neighbours_after = nets_.stream().filter(net->net.internal_sides.contains(face)).map(net->net.neighbour_positions).findFirst().map(HashSet::new).orElse(new HashSet<>());
            for(BlockPos p:net_neighbours_after) { if(!net_neighbours_before.contains(p)) connected.add(p); }
            for(BlockPos p:net_neighbours_before) { if(!net_neighbours_after.contains(p)) disconnected.add(p); }
          }
        }
        if(connected.isEmpty() && disconnected.isEmpty()) {
          setSidePower(face, initial_side_power);
        } else {
          setSidePower(face, 0);
          nets_.forEach(net->{ if(net.internal_sides.contains(face)) net.power=0; });
          disconnected.forEach((p)->{
            BlockEntity te = getLevel().getBlockEntity(p);
            getLevel().getBlockState(p).neighborChanged(getLevel(), p, getBlock(), pos, false);
            if(te instanceof TrackBlockEntity) ((TrackBlockEntity)te).updateConnections(1);
          });
          connected.forEach((p)->{
            BlockEntity te = getLevel().getBlockEntity(p);
            if(te instanceof TrackBlockEntity) ((TrackBlockEntity)te).updateConnections(1);
            getLevel().getBlockState(p).neighborChanged(getLevel(), p, getBlock(), pos, false);
            getBlock().neighborChanged(getBlockState(), getLevel(), getBlockPos(), getBlock(), p, false);
          });
        }
        sync(true);
      }
      return material_use;
    }

    private static final List<Vec3i> updatepower_order = new ArrayList<>();

    public Map<BlockPos,BlockPos> updateAllPowerValuesFromAdjacent()
    {
      if(updatepower_order.isEmpty()) {
        for(Direction side:Direction.values())
          updatepower_order.add(new Vec3i(0,0,0).relative(side,1));
        for(int x=-1; x<=1; ++x)
          for(int y=-1; y<=1; ++y)
            for(int z=-1; z<=1; ++z)
              if(Math.abs(x)+Math.abs(y)+Math.abs(z) == 2) updatepower_order.add(new Vec3i(x,y,z));
      }
      final Map<BlockPos,BlockPos> all_change_notifications = new HashMap<>();
      for(Vec3i ofs:updatepower_order) {
        handleNeighborChanged(getBlockPos().offset(ofs)).forEach(all_change_notifications::putIfAbsent);
      }
      return all_change_notifications;
    }

    private void spawnRedsoneItems(int count)
    {
      if(count <= 0) return;
      final ItemEntity e = new ItemEntity(getLevel(), getBlockPos().getX()+.5, getBlockPos().getY()+.5, getBlockPos().getZ()+.5, new ItemStack(Items.REDSTONE, count));
      e.setDefaultPickUpDelay();
      e.setDeltaMovement(new Vec3(getLevel().getRandom().nextDouble()-.5, getLevel().getRandom().nextDouble()-.5, getLevel().getRandom().nextDouble()).scale(0.1));
      getLevel().addFreshEntity(e);
    }

    private RedstoneTrackBlock getBlock()
    { return ModContent.references.TRACK_BLOCK; }

    public boolean handlePostPlacement(Direction facing, BlockState facingState, BlockPos fromPos)
    {
      boolean update_neighbours = false;
      if(!RedstoneTrackBlock.canBePlacedOnFace(facingState, getLevel(), fromPos, facing.getOpposite())) {
        final long to_remove = defs.connections.getAllElementsOnFace(facing);
        final long new_flags = (state_flags_ & ~to_remove);
        if(new_flags != state_flags_) {
          if(trace_) Auxiliaries.logWarn(String.format("SHUP: %s <-%s(=%s) removed.", posstr(getBlockPos()), posstr(fromPos), facingState.getBlock().getDescriptionId()));
          int count = getRedstoneDustCount();
          state_flags_ = new_flags;
          count -= getRedstoneDustCount();
          spawnRedsoneItems(count);
          updateConnections(1);
          update_neighbours = true;
        }
      }
      Block bltv = block_change_tracking_[facing.get3DDataValue()];
      if(bltv != facingState.getBlock()) {
        if(bltv == null) bltv = Blocks.AIR;
        if(trace_) Auxiliaries.logWarn(String.format("SHUP: %s <-%s changed (%s->%s).", posstr(getBlockPos()), posstr(fromPos), bltv.getDescriptionId(), facingState.getBlock().getDescriptionId()));
        block_change_tracking_[facing.get3DDataValue()] = facingState.getBlock();
        update_neighbours = true;
      }
      if(update_neighbours) {
        final Level world = getLevel();
        final Block block = getBlock();
        handleNeighborChanged(fromPos).forEach((chpos, frpos)->world.neighborChanged(chpos, block, frpos));
      }
      return (getWireFlags()!=0);
    }

    private int getNonWireSignal(Level world, BlockPos pos, Direction redstone_side)
    {
      // According to world.getRedstonePower():
      getBlock().disablePower(true);
      final BlockState state = world.getBlockState(pos);
      int p = (!state.is(Blocks.REDSTONE_WIRE) && (!state.is(getBlock()))) ? state.getSignal(world, pos, redstone_side) : 0;
      //if(trace_) Auxiliaries.logWarn(String.format("GETNWS from [%s @ %s] = %dw", posstr(getPos()), redstone_side, p));
      if(!RsSignals.canEmitWeakPower(state, world, pos, redstone_side)) { getBlock().disablePower(false); return p; }
      // According to world.getStrongPower():
      for(Direction rs_side: Direction.values()) {
        final BlockPos side_pos = pos.relative(rs_side);
        final BlockState side_state = world.getBlockState(side_pos);
        if(side_state.is(Blocks.REDSTONE_WIRE) || side_state.is(getBlock())) continue;
        final int p_in = side_state.getDirectSignal(world, side_pos, rs_side);
        if(p_in > p) {
          p = p_in;
          if(p >= 15) break;
        }
      }
      getBlock().disablePower(false);
      //if(trace_) Auxiliaries.logWarn(String.format("GETNWS from [%s @ %s] = %dS", posstr(getPos()), redstone_side, p));
      return p;
    }

    public Map<BlockPos,BlockPos> handleNeighborChanged(BlockPos fromPos)
    {
      final Level world = getLevel();
      final Map<BlockPos,BlockPos> change_notifications = new LinkedHashMap<>();
      boolean power_changed = false;
      for(TrackNet net: nets_) {
        if(!net.neighbour_positions.contains(fromPos)) continue;
        if(trace_) Auxiliaries.logWarn(String.format("NBCH: %s from %s", posstr(getBlockPos()), posstr(fromPos)));
        int pmax = 0;
        for(int i = 0; i<net.neighbour_positions.size(); ++i) {
          final BlockPos ext_pos = net.neighbour_positions.get(i);
          change_notifications.put(ext_pos, getBlockPos());
          final Direction ext_side = net.neighbour_sides.get(i);
          final BlockState ext_state = level.getBlockState(ext_pos);
          if(ext_state.is(Blocks.REDSTONE_WIRE)) {
            if(pmax < 15) {
              final int p_vanilla_wire = Math.max(0, ext_state.getValue(RedStoneWireBlock.POWER)-1);
              pmax = Math.max(pmax, p_vanilla_wire);
            }
          } else if(ext_state.is(getBlock())) {
            if(pmax < 15) {
              final int p_track = RedstoneTrackBlock.tile(world, ext_pos).map(te->Math.max(0, te.getSidePower(ext_side)-1)).orElse(0);
              pmax = Math.max(pmax, p_track);
            }
          } else {
            final Direction eside = ext_side.getOpposite();
            final int p_nowire = getNonWireSignal(world, ext_pos, eside);
            pmax = Math.max(pmax, p_nowire);
            if((!ext_state.isSignalSource()) && (p_nowire == 0) && ext_state.isRedstoneConductor(world, ext_pos)) {
              for(Direction update_direction: defs.REDSTONE_UPDATE_DIRECTIONS) {
                if(ext_side == update_direction) continue;
                change_notifications.putIfAbsent(ext_pos.relative(update_direction), ext_pos);
              }
            }
          }
        }
        if(net.power != pmax) {
          if(trace_) Auxiliaries.logWarn(String.format("NBCH: %s net power %d->%d", posstr(getBlockPos()), net.power, pmax));
          net.power = pmax;
          power_changed = true;
        }
        for(Direction side: net.internal_sides) {
          if(getSidePower(side) != pmax) {
            setSidePower(side, pmax);
            power_changed = true;
          }
        }
      }
      if(power_changed) {
        if(trace_ && change_notifications.size()>0) {
          Auxiliaries.logWarn(String.format("NBCH: %s updates: [%s]", posstr(getBlockPos()), change_notifications.entrySet().stream().map(kv-> posstr(kv.getKey())+">"+posstr(kv.getValue())).collect(Collectors.joining(" ; "))));
        }
        sync(true);
        return change_notifications;
      } else {
        return Collections.emptyMap();
      }
    }

    private String posstr(BlockPos pos)
    { return "[" +pos.getX()+ "," +pos.getY()+ "," +pos.getZ()+ "]"; }

    @SuppressWarnings("all")
    private boolean isRedstoneInsulator(BlockState state, BlockPos pos)
    { return state.is(Blocks.GLASS); } // don't care about isRedstoneConductor(), messes up depending on block implementations.

    private void updateConnections(int recursion_left)
    {
      final Set<BlockPos> all_neighbours = new HashSet<>();
      final int[] current_side_powers = {0,0,0,0,0,0};
      nets_.forEach((net)->{
        net.internal_sides.forEach(ps->current_side_powers[ps.ordinal()] = net.power);
        all_neighbours.addAll(net.neighbour_positions);
      });
      if(trace_) Auxiliaries.logWarn(String.format("UCON: %s SIDPW: [%01x %01x %01x %01x %01x %01x]", posstr(getBlockPos()), current_side_powers[0], current_side_powers[1], current_side_powers[2], current_side_powers[3], current_side_powers[4], current_side_powers[5]));
      nets_.clear();
      final Set<TrackBlockEntity> track_connection_updates = new HashSet<>();
      final long[] internal_connected_sides = {0,0,0,0,0,0};
      final long[] external_connected_routes = {0,0,0,0,0,0};
      // Own internal and external connections.
      {
        long external_connection_flags = getStateFlags() & (defs.STATE_FLAG_WIR_MASK|defs.STATE_FLAG_CON_MASK);
        for(Map.Entry<Long,Tuple<Direction,Direction>> kv: defs.connections.INTERNAL_EDGE_CONNECTION_MAPPING.entrySet()) {
          final long wire_bit_pair = kv.getKey();
          if((getStateFlags() & wire_bit_pair) != wire_bit_pair) continue; // no internal connection.
          external_connection_flags &= ~wire_bit_pair;
          for(int i=0; i<6; ++i) {
            if(((0xfL<<(4*i)) & wire_bit_pair) == 0) continue;
            internal_connected_sides[i] |= wire_bit_pair;
          }
        }
        if(trace_) Auxiliaries.logWarn(String.format("UCON: %s CONFL: ext:%08x | int:[%08x %08x %08x %08x %08x %08x]", posstr(getBlockPos()), external_connection_flags, internal_connected_sides[0], internal_connected_sides[1], internal_connected_sides[2], internal_connected_sides[3], internal_connected_sides[4], internal_connected_sides[5]));
        // Condense internal connections.
        for(int k=0; k<2; ++k) {
          for(int i=0; i<6; ++i) {
            if(internal_connected_sides[i] == 0) continue;
            for(int j=i+1; j<6; ++j) {
              if((internal_connected_sides[i] & internal_connected_sides[j]) == 0) continue;
              internal_connected_sides[i] |= internal_connected_sides[j];
              internal_connected_sides[j] = 0;
            }
          }
        }
        // Track nets
        for(int i=0; i<6; ++i) {
          if(internal_connected_sides[i] != 0) {
            for(int j=i; j<6; ++j) {
              final long mask = (0xfL<<(4*j));
              if((internal_connected_sides[i] & mask) == 0) continue;
              final long bulk = (0x1L<<(defs.STATE_FLAG_CON_POS+j));
              external_connected_routes[i] |= (external_connection_flags & (mask|bulk));
              external_connection_flags &= ~(mask|bulk);
            }
          } else {
            final long mask = (0xfL<<(4*i));
            final long bulk = (0x1L<<(defs.STATE_FLAG_CON_POS+i));
            external_connected_routes[i] |= (external_connection_flags & (mask|bulk));
            external_connection_flags &= ~(mask|bulk);
          }
        }
        if(trace_) {
          Auxiliaries.logWarn(String.format("UCON: %s CONSD: ext:%08x | int:[%08x %08x %08x %08x %08x %08x]", posstr(getBlockPos()), external_connection_flags, internal_connected_sides[0], internal_connected_sides[1], internal_connected_sides[2], internal_connected_sides[3], internal_connected_sides[4], internal_connected_sides[5]));
          Auxiliaries.logWarn(String.format("UCON: %s CONRT: ext:%08x | ext:[%08x %08x %08x %08x %08x %08x]", posstr(getBlockPos()), external_connection_flags, external_connected_routes[0], external_connected_routes[1], external_connected_routes[2], external_connected_routes[3], external_connected_routes[4], external_connected_routes[5]));
        }
      }
      // Net list.
      {
        Set<Direction> used_sides = new HashSet<>();
        for(int i=0; i<6; ++i) {
          if(external_connected_routes[i] == 0) continue;
          final Set<Direction> power_sides = new HashSet<>();
          final Set<Direction> internal_sides = new HashSet<>();
          final List<BlockPos>  block_positions = new ArrayList<>(6);
          final List<Direction> block_sides = new ArrayList<>(6);
          for(int j=0; j<6; ++j) {
            final long mask = (0xfL<<(4*j));
            final long bulk = (0x1L<<(defs.STATE_FLAG_CON_POS+j));
            final Direction side = connections.CONNECTION_BIT_ORDER[j];
            // Internal net route sides
            if((internal_connected_sides[i] & mask) != 0) {
              internal_sides.add(side);
            }
            // External wire net routes
            if((external_connected_routes[i] & mask) != 0) {
              for(int k=0; k<4; ++k) {
                final long wire_bit = (0x1L<<(4*j+k));
                if((external_connected_routes[i] & wire_bit) == 0) continue;
                final Tuple<Direction,Direction> side_dir = defs.connections.getWireBitSideAndDirection(wire_bit);
                final Direction tsid = side_dir.getA();
                final Direction tdir = side_dir.getB();
                final BlockPos wire_pos = getBlockPos().relative(tdir);
                final BlockState wire_state = getLevel().getBlockState(wire_pos);
                boolean diagonal_check = false;
                if(wire_state.is(getBlock())) {
                  // adjacent track
                  long adjacent_mask = defs.connections.getWireBit(tsid, tdir.getOpposite());
                  TrackBlockEntity adj_te = RedstoneTrackBlock.tile(getLevel(), wire_pos).orElse(null);
                  if((adj_te==null) || (adj_te.getStateFlags() & adjacent_mask) != adjacent_mask) {
                    diagonal_check = true;
                  } else {
                    block_positions.add(wire_pos);
                    block_sides.add(tsid);
                    internal_sides.add(side);
                    power_sides.add(tdir);
                    track_connection_updates.add(adj_te);
                    continue;
                  }
                }
                // adjacent vanilla wire
                if((!diagonal_check) && wire_state.is(Blocks.REDSTONE_WIRE)) {
                  // adjacent vanilla redstone wire, only connected on the bottom face.
                  if(side!=Direction.DOWN) {
                    diagonal_check = true;
                  } else {
                    block_positions.add(wire_pos);
                    block_sides.add(tdir.getOpposite()); // NOT the redstone side, the real face.
                    internal_sides.add(side);
                    power_sides.add(tdir);
                    continue;
                  }
                }
                // power source
                if((!diagonal_check) && wire_state.isSignalSource()) {
                  // adjacent power block
                  block_positions.add(wire_pos);
                  block_sides.add(tdir.getOpposite()); // real face.
                  internal_sides.add(side);
                  power_sides.add(tdir);
                  continue;
                }
                // diagonal track
                {
                  final BlockPos track_pos = wire_pos.relative(tsid);
                  final BlockState track_state = getLevel().getBlockState(track_pos);
                  if(track_state.is(getBlock())) {
                    long adjacent_mask = defs.connections.getWireBit(tdir.getOpposite(), tsid.getOpposite());
                    TrackBlockEntity adj_te = RedstoneTrackBlock.tile(getLevel(), track_pos).orElse(null);
                    if((adj_te==null) || (adj_te.getStateFlags() & adjacent_mask) != adjacent_mask) continue;
                    block_positions.add(track_pos);
                    block_sides.add(tdir.getOpposite());
                    power_sides.add(tdir);
                    internal_sides.add(side);
                    track_connection_updates.add(adj_te);
                    continue;
                  }
                }
                // air or full block
                if(!isRedstoneInsulator(wire_state, wire_pos)) {
                  block_positions.add(wire_pos);
                  block_sides.add(tdir.getOpposite());
                  internal_sides.add(side);
                  power_sides.add(tdir);
                  continue;
                }
              }
            }
            // External bulk connector net routes
            if((external_connected_routes[i] & bulk) != 0) {
              final BlockPos bulk_pos = getBlockPos().relative(side);
              final BlockState bulk_state = getLevel().getBlockState(bulk_pos);
              if(isRedstoneInsulator(bulk_state, bulk_pos)) continue;
              block_positions.add(bulk_pos);
              block_sides.add(side.getOpposite()); // NOT the redstone side, the real face.
              internal_sides.add(side);
              power_sides.add(side);
            }
          }
          // Update net
          if(!block_positions.isEmpty()) {
            TrackNet net = new TrackNet(block_positions, block_sides, new ArrayList<>(internal_sides), new ArrayList<>(power_sides));
            net.power = net.internal_sides.stream().mapToInt(side->current_side_powers[side.ordinal()]).max().orElse(0);
            nets_.add(net);
            used_sides.addAll(internal_sides);
          }
        }
        Arrays.stream(Direction.values()).filter(side->!used_sides.contains(side)).forEach(side->setSidePower(side, 0));
      }
      setChanged();
      if(trace_) {
        final String poss = posstr(getBlockPos());
        for(TrackNet net:nets_) {
          final List<String> ss = new ArrayList<>();
          for(int i = 0; i<net.neighbour_positions.size(); ++i) ss.add(posstr(net.neighbour_positions.get(i)) + ":" + net.neighbour_sides.get(i).toString());
          String int_sides = net.internal_sides.stream().map(Direction::toString).collect(Collectors.joining(","));
          String pwr_sides = net.power_sides.stream().map(Direction::toString).collect(Collectors.joining(","));
          Auxiliaries.logWarn(String.format("UCON: %s adj:%s | ints:%s | pwrs:%s", poss, String.join(", ", ss), int_sides, pwr_sides));
        }
      }
      if(recursion_left > 0) {
        for(TrackBlockEntity te:track_connection_updates) {
          if(trace_) Auxiliaries.logWarn(String.format("UCON: %s UPDATE NET OF %s", posstr(getBlockPos()), posstr(te.getBlockPos())));
          te.updateConnections(recursion_left-1);
        }
      }
      // Removed/added connections
      {
        nets_.stream().filter((net)->net.power > 0).forEach((net)->all_neighbours.addAll(net.neighbour_positions));
        final Level world = getLevel();
        final BlockState state = getBlockState();
        all_neighbours.forEach((pos)->{
          final BlockState st = world.getBlockState(pos);
          if(trace_) Auxiliaries.logWarn(String.format("UCON: %s UPDATE TRACK CHANGES TO %s.", posstr(getBlockPos()), posstr(pos)));
          st.neighborChanged(world, pos, state.getBlock(), getBlockPos(), false);
          world.updateNeighborsAt(pos, st.getBlock());
        });
      }
    }
  }

}
