/*
 * @file RedstoneTrack.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.blocks;

import com.google.common.collect.ImmutableMap;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.PushReaction;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.IntArrayNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.LongArrayNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.particles.RedstoneParticleData;
import net.minecraft.pathfinding.PathType;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.IBooleanFunction;
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
import net.minecraftforge.common.util.Constants.NBT;
import wile.redstonepen.ModContent;
import wile.redstonepen.blocks.RedstoneTrack.defs.connections;
import wile.redstonepen.libmc.blocks.StandardBlocks;
import wile.redstonepen.libmc.detail.Auxiliaries;
import wile.redstonepen.libmc.detail.Inventories;
import wile.redstonepen.libmc.detail.Networking;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;


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

      public static final ImmutableMap<Long,Tuple<Direction,Direction>> WIRE_FACE_DIRECTION_MAPPING = new ImmutableMap.Builder<Long,Tuple<Direction,Direction>>()
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
        public static final long getBulkConnectorBit(Direction face)
        { return connections.BULK_FACE_MAPPING_REV.get(face); }

        /**
         * Returns the state bit for a wire (with direction `wire_direction`)
         * on a specific face.
         */
        public static final long getWireBit(Direction face, Direction wire_direction)
        {
          return connections.WIRE_FACE_DIRECTION_MAPPING.entrySet().stream()
            .filter(kv->kv.getValue().getA()==face && kv.getValue().getB()==wire_direction)
            .findFirst()
            .map(kv->kv.getKey()).orElse(0L);
        }

        public static final Tuple<Direction,Direction> getWireBitSideAndDirection(long wirebit)
        { return WIRE_FACE_DIRECTION_MAPPING.getOrDefault(wirebit, new Tuple<>(Direction.DOWN,Direction.DOWN)); }

        public static final List<Direction> getVanillaWireConnectionDirections(long mask)
        {
          if((mask & 0x0000000fL)==0) return Collections.emptyList();
          final List<Direction> r = new ArrayList<>(4);
          if((mask & 0x00000001L) != 0) r.add(Direction.NORTH);
          if((mask & 0x00000002L) != 0) r.add(Direction.SOUTH);
          if((mask & 0x00000004L) != 0) r.add(Direction.EAST);
          if((mask & 0x00000008L) != 0) r.add(Direction.WEST);
          return r;
        }

        public static final boolean hasVanillaWireConnection(long mask, Direction side)
        {
          switch(side) {
            case NORTH: return ((mask & 0x00000001L) != 0);
            case SOUTH: return ((mask & 0x00000002L) != 0);
            case EAST : return ((mask & 0x00000004L) != 0);
            case WEST : return ((mask & 0x00000008L) != 0);
            default: return false;
          }
        }

        public static final boolean hasBulkConnection(long mask, Direction side)
        { return ((connections.BULK_FACE_MAPPING_REV.get(side) & mask) != 0); }

        public static final boolean hasRedstoneConnection(long mask, Direction side)
        {
          switch(side) {
            case DOWN : return ((mask & 0x01222200L) != 0);
            case UP   : return ((mask & 0x02111100L) != 0);
            case NORTH: return ((mask & 0x04440011L) != 0);
            case SOUTH: return ((mask & 0x08880022L) != 0);
            case EAST : return ((mask & 0x10004444L) != 0);
            case WEST : return ((mask & 0x20008888L) != 0);
            default: return false;
          }
        }

        public static final long getAllElementsOnFace(Direction face)
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
          VoxelShape shape = VoxelShapes.empty();
          if((faces & 0x01)!=0) shape = VoxelShapes.combineAndSimplify(shape, DOWN_SHAPE, IBooleanFunction.OR);
          if((faces & 0x02)!=0) shape = VoxelShapes.combineAndSimplify(shape, UP_SHAPE, IBooleanFunction.OR);
          if((faces & 0x04)!=0) shape = VoxelShapes.combineAndSimplify(shape, NORTH_SHAPE, IBooleanFunction.OR);
          if((faces & 0x08)!=0) shape = VoxelShapes.combineAndSimplify(shape, SOUTH_SHAPE, IBooleanFunction.OR);
          if((faces & 0x10)!=0) shape = VoxelShapes.combineAndSimplify(shape, EAST_SHAPE, IBooleanFunction.OR);
          if((faces & 0x20)!=0) shape = VoxelShapes.combineAndSimplify(shape, WEST_SHAPE, IBooleanFunction.OR);
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

  public static class RedstoneTrackBlock extends StandardBlocks.WaterLoggable
  {
    public RedstoneTrackBlock(long config, Block.Properties builder)
    { super(config, builder); }

    public static Optional<TrackTileEntity> tile(IBlockReader world, BlockPos pos)
    { final TileEntity te=world.getTileEntity(pos); return (((te instanceof TrackTileEntity) && (!te.isRemoved())) ? Optional.of((TrackTileEntity)te) : Optional.empty()); }

    public static boolean canBePlacedOnFace(BlockState state, World world, BlockPos pos, Direction face)
    {
      if(state.getBlock() instanceof PistonBlock) {
        Direction pface = state.get(PistonBlock.FACING);
        return (face != pface);
      }
      if(state.getBlock() instanceof MovingPistonBlock) return true;
      if(state.isIn(Blocks.HOPPER)) return (face == Direction.UP);
      return state.isSolidSide(world, pos, face);
    }

    private boolean can_provide_power_ = true;

    //------------------------------------------------------------------------------------------------------------------

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    { super.fillStateContainer(builder); }

    @Override
    public boolean hasTileEntity(BlockState state)
    { return true; }

    @Override
    @Nullable
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    { return new RedstoneTrack.TrackTileEntity(); }

    @Override
    public boolean hasDynamicDropList()
    { return true; }

    @Override
    public List<ItemStack> dropList(BlockState state, World world, @Nullable TileEntity te, boolean explosion)
    {
      if(!(te instanceof TrackTileEntity)) return Collections.emptyList();
      int num_connections = ((TrackTileEntity)te).getRedstoneDustCount();
      if(num_connections <= 0) return Collections.emptyList();
      return Collections.singletonList(new ItemStack(Items.REDSTONE, num_connections));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockItemUseContext context)
    { return context.getWorld().getBlockState(context.getPos()).isReplaceable(context) ? super.getStateForPlacement(context) : null; }

    @Override
    public Item asItem()
    { return Items.REDSTONE; }

    @Override
    public boolean allowsMovement(BlockState state, IBlockReader world, BlockPos pos, PathType type)
    { return true; }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext selectionContext)
    {
      final int wires = tile(world, pos).map(TrackTileEntity::getWireFlags).orElse(0);
      final int faces = (((wires & 0x00000f) != 0) ? 0x01 : 0)
        | (((wires & 0x0000f0) != 0) ? 0x02 : 0)
        | (((wires & 0x000f00) != 0) ? 0x04 : 0)
        | (((wires & 0x00f000) != 0) ? 0x08 : 0)
        | (((wires & 0x0f0000) != 0) ? 0x10 : 0)
        | (((wires & 0xf00000) != 0) ? 0x20 : 0);
      return defs.shape.get(faces);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader world, BlockPos pos,  ISelectionContext selectionContext)
    { return VoxelShapes.empty(); }

    @Override
    public boolean propagatesSkylightDown(BlockState state, IBlockReader reader, BlockPos pos)
    { return !state.get(WATERLOGGED); }

    @Override
    public PushReaction getPushReaction(BlockState state)
    { return PushReaction.DESTROY; }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isTransparent(BlockState state)
    { return true; }

    @Deprecated
    @SuppressWarnings("deprecation")
    public int getOpacity(BlockState state, IBlockReader worldIn, BlockPos pos)
    { return 0; }

    @Deprecated
    @SuppressWarnings("deprecation")
    public BlockRenderType getRenderType(BlockState state)
    { return BlockRenderType.ENTITYBLOCK_ANIMATED; }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isValidPosition(BlockState state, IWorldReader world, BlockPos pos)
    { return true; }

    @Deprecated
    public boolean canConnectRedstone(BlockState state, IBlockReader world, BlockPos pos, @Nullable Direction side)
    { return (side != null) && (tile(world,pos).map(te->te.hasVanillaRedstoneConnection(side.getOpposite()))).orElse(false); }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canProvidePower(BlockState state)
    { return can_provide_power_; }

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
    { return can_provide_power_ ? tile(world, pos).map(te->te.getRedstonePower(redsrone_side, true)).orElse(0) : 0; }

    @Deprecated
    @SuppressWarnings("deprecation")
    public int getStrongPower(BlockState state, IBlockReader world, BlockPos pos, Direction redsrone_side)
    { return can_provide_power_ ? tile(world, pos).map(te->te.getRedstonePower(redsrone_side, false)).orElse(0) : 0; }

    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos pos, Random rnd)
    { if(!tile(world,pos).map(te->te.sync(false)).orElse(false)) world.removeBlock(pos, false); }

    @Override
    public BlockState updatePostPlacement(BlockState state, Direction facing, BlockState facingState, IWorld world, BlockPos pos, BlockPos facingPos)
    {
      if(!world.isRemote()) {
        if(tile(world, pos).map(te->te.handlePostPlacement(facing, facingState, facingPos)).orElse(true)) {
          world.getPendingBlockTicks().scheduleTick(pos, this, 1);
        } else {
          world.removeBlock(pos, false);
        }
      }
      return super.updatePostPlacement(state, facing, facingState, world, pos, facingPos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean isMoving)
    {
      if(oldState.isIn(state.getBlock()) || world.isRemote()) return;
      tile(world,pos).ifPresent(te->te.updateConnections());
      notifyAdjacent(world, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving)
    {
      if(isMoving || state.isIn(newState.getBlock())) return;
      super.onReplaced(state, world, pos, newState, isMoving);
      if(world.isRemote()) return;
      notifyAdjacent(world, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult rtr)
    {
      {
        ItemStack stack = player.getHeldItem(hand);
        if((!stack.isEmpty()) && (stack.getItem()!=Items.REDSTONE) && (stack.getItem()!=ModContent.PEN_ITEM)) {
          BlockPos behind_pos = pos.offset(rtr.getFace());
          BlockState behind_state = world.getBlockState(behind_pos);
          if(behind_state.isNormalCube(world, behind_pos)) {
            return behind_state.getBlock().onBlockActivated(behind_state,world,behind_pos, player, hand, rtr);
          }
          return ActionResultType.PASS;
        }
      }
      if(world.isRemote()) return ActionResultType.SUCCESS;
      TrackTileEntity te = tile(world, pos).orElse(null);
      if(te==null) return ActionResultType.FAIL;
      int redstone_use = te.handleActivation(pos, player, hand, rtr.getFace(), rtr.getHitVec(), false);
      if(redstone_use == 0) {
        return ActionResultType.PASS;
      } else if(redstone_use < 0) {
        final ItemStack stack = player.getHeldItem(hand);
        redstone_use = -redstone_use;
        if(stack.getItem() == ModContent.PEN_ITEM) {
          if(stack.getDamage() >= redstone_use) {
            stack.setDamage(stack.getDamage()-redstone_use);
          } else {
            redstone_use -= stack.getDamage();
            stack.setDamage(0);
            Inventories.give(player, new ItemStack(Items.REDSTONE, redstone_use));
          }
        } else if(stack.getItem() == Items.REDSTONE) {
          if(stack.getCount() <= stack.getMaxStackSize()-redstone_use) {
            stack.grow(redstone_use);
          } else {
            Inventories.give(player, new ItemStack(Items.REDSTONE, redstone_use));
          }
        } else {
          Inventories.give(player, new ItemStack(Items.REDSTONE, redstone_use));
        }
        if(te.getWireFlags() == 0) {
          world.setBlockState(pos, state.getFluidState().getBlockState(), 1|2);
        } else {
          te.updateAllPowerValuesFromAdjacent();
        }
        state.updateNeighbours(world, pos, 1|2);
        notifyAdjacent(world, pos);
        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 0.4f, 2f);
        return ActionResultType.CONSUME;
      } else {
        final ItemStack stack = player.getHeldItem(hand);
        if(stack.getItem() == ModContent.PEN_ITEM) {
          stack.setDamage(stack.getDamage()+redstone_use);
          if(stack.getDamage() >= stack.getMaxDamage()) {
            player.setHeldItem(hand, ItemStack.EMPTY);
          }
        } else if(stack.getItem() == Items.REDSTONE) {
          stack.shrink(redstone_use);
        }
        world.playSound(null, pos, SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 0.4f, 2.4f);
        te.updateAllPowerValuesFromAdjacent();
        state.updateNeighbours(world, pos, 1|2);
        notifyAdjacent(world, pos);
        return ActionResultType.CONSUME;
      }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, World world, BlockPos pos, Block fromBlock, BlockPos fromPos, boolean isMoving)
    {
      if(world.isRemote()) return;
      final Map<BlockPos,BlockPos> blocks_to_update = tile(world, pos).map(te->te.handleNeighborChanged(fromBlock, fromPos)).orElse(Collections.emptyMap());
      if(blocks_to_update.isEmpty()) return;
      for(Map.Entry<BlockPos,BlockPos> update_pos:blocks_to_update.entrySet()) {
        //Auxiliaries.logWarn(String.format("NCUP:    [%d,%d,%d] -> [%d,%d,%d]", pos.getX(), pos.getY(), pos.getZ(), update_pos.getX(), update_pos.getY(), update_pos.getZ()));
        world.neighborChanged(update_pos.getKey(), this, update_pos.getValue());
      }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void updateDiagonalNeighbors(BlockState state, IWorld worldIn, BlockPos pos, int flags, int recursionLeft)
    {}

    @OnlyIn(Dist.CLIENT)
    private void spawnPoweredParticle(World world, Random rand, BlockPos pos, Vector3f color, Direction from, Direction to, float minChance, float maxChance) {
      float f = maxChance - minChance;
      if(rand.nextFloat() < 0.3f * f) {
        double c1 = 0.4375;
        double c2 = minChance + f * rand.nextFloat();
        double p0 = 0.5 + (c1 * from.getXOffset()) + (c2*.4 * to.getXOffset());
        double p1 = 0.5 + (c1 * from.getYOffset()) + (c2*.4 * to.getYOffset());
        double p2 = 0.5 + (c1 * from.getZOffset()) + (c2*.4 * to.getZOffset());
        world.addParticle(new RedstoneParticleData(color.getX(),color.getY(),color.getZ(),1.0F), pos.getX()+p0, pos.getY()+p1, pos.getZ()+p2, 0, 0., 0);
      }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void animateTick(BlockState state, World world, BlockPos pos, Random rand)
    {
      if(rand.nextFloat() > 0.4) return;
      final TrackTileEntity te = tile(world,pos).orElse(null);
      if((te == null) || ((te.getStateFlags() & defs.STATE_FLAG_PWR_MASK) == 0)) return;
      final Vector3f color = new Vector3f(0.6f,0,0);
      for(Direction side: Direction.values()) {
        int p = te.getSidePower(side);
        if(p == 0) continue;
        spawnPoweredParticle(world, rand, pos, color, side, side.getOpposite(), -0.5F, 0.5F);
      }
    }

    //------------------------------------------------------------------------------------------------------------------

    public void checkSmartPlacement(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult rtr)
    {
      if(world.isRemote()) return;
      final ItemStack pen = player.getHeldItem(hand);
      if((pen.getItem() != ModContent.PEN_ITEM) || (pen.getDamage() >= pen.getMaxDamage()-2)) return;
      final TrackTileEntity te = tile(world,pos).orElse(null);
      if(te == null) return;
      final Tuple<Direction,Direction> side_dir = defs.connections.getWireBitSideAndDirection(te.getWireFlags());
      final Direction face = side_dir.getA();
      final Direction dir = side_dir.getB();
      if((face==Direction.DOWN) && (dir==Direction.DOWN)) return; // more than one wire flag set.
      int num_placed = 0;
      long flags_to_add = 0;
      for(Direction d: Direction.values()) {
        if(pen.getDamage()+num_placed >= pen.getMaxDamage()-2) break;
        if((d==face) || (d==face.getOpposite()) || (d==dir)) continue;
        final BlockState ostate = world.getBlockState(pos.offset(d));
        if(ostate.isIn(this)) {
          final TrackTileEntity ote = tile(world, pos.offset(d)).orElse(null);
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
        final BlockPos opos = pos.offset(odir);
        final BlockState ostate = world.getBlockState(opos);
        if((!ostate.canProvidePower()) && (!ostate.canConnectRedstone(world, pos, odir))) {
          flags_to_add |= connections.getWireBit(face, odir);
          ++num_placed;
        }
      }
      if(num_placed > 0) {
        int n_added = te.addWireFlags(flags_to_add);
        te.sync(true);
        pen.damageItem(n_added, player, (ent->{}));
        te.updateConnections(2);
        te.updateAllPowerValuesFromAdjacent();
        state.updateNeighbours(world, pos, 1|2);
        notifyAdjacent(world, pos);
      }
    }

    private void disablePower(boolean disable)
    { can_provide_power_ = !disable; }

    public void notifyAdjacent(World world, BlockPos pos)
    {
      world.notifyNeighborsOfStateChange(pos, this);
      for(Direction side: Direction.values()) {
        final BlockPos ppos = pos.offset(side);
        world.notifyNeighborsOfStateExcept(ppos, world.getBlockState(ppos).getBlock(), side.getOpposite());
      }
    }

  }

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entity
  //--------------------------------------------------------------------------------------------------------------------

  public static class TrackTileEntity extends TileEntity implements Networking.IPacketTileNotifyReceiver
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

    public TrackTileEntity()
    { this(ModContent.TET_TRACK); }

    public TrackTileEntity(TileEntityType<?> te_type)
    { super(te_type); }

    public CompoundNBT readnbt(CompoundNBT nbt)
    {
      state_flags_ = nbt.getLong("sflags");
      nets_.clear();
      if(nbt.contains("nets", NBT.TAG_LIST)) {
        final ListNBT lst = nbt.getList("nets", NBT.TAG_COMPOUND);
        try {
          for(int i=0; i<lst.size(); ++i) {
            CompoundNBT route_nbt = lst.getCompound(i);
            nets_.add(new TrackNet(
              Arrays.stream(route_nbt.getLongArray("npos")).mapToObj(lpos->BlockPos.fromLong(lpos)).collect(Collectors.toList()),
              Arrays.stream(route_nbt.getIntArray("nsid")).mapToObj(Direction::byIndex).collect(Collectors.toList()),
              Arrays.stream(route_nbt.getIntArray("ifac")).mapToObj(Direction::byIndex).collect(Collectors.toList()),
              Arrays.stream(route_nbt.getIntArray("pfac")).mapToObj(Direction::byIndex).collect(Collectors.toList()),
              route_nbt.getInt("power")
            ));
          }
        } catch(Throwable ex) {
          nets_.clear();
          Auxiliaries.logError("Dropped invalid NBT for Redstone Track at pos " + getPos());
        }
      }
      return nbt;
    }

    private CompoundNBT writenbt(CompoundNBT nbt)
    { return writenbt(nbt, false); }

    private CompoundNBT writenbt(CompoundNBT nbt, boolean sync_packet)
    {
      nbt.putLong("sflags", state_flags_);
      if(sync_packet) return nbt;
      if(!nets_.isEmpty()) {
        final ListNBT lst = new ListNBT();
        for(TrackNet net: nets_) {
          CompoundNBT route_nbt = new CompoundNBT();
          route_nbt.putInt("power", net.power);
          route_nbt.put("npos", new LongArrayNBT(net.neighbour_positions.stream().map(BlockPos::toLong).collect(Collectors.toList())));
          route_nbt.put("nsid", new IntArrayNBT(net.neighbour_sides.stream().map(Direction::getIndex).collect(Collectors.toList())));
          route_nbt.put("ifac", new IntArrayNBT(net.internal_sides.stream().map(Direction::getIndex).collect(Collectors.toList())));
          route_nbt.put("pfac", new IntArrayNBT(net.power_sides.stream().map(Direction::getIndex).collect(Collectors.toList())));
          lst.add(route_nbt);
        }
        nbt.put("nets", lst);
      }
      return nbt;
    }

    @Override
    public void read(BlockState state, CompoundNBT nbt)
    { super.read(state, nbt); readnbt(nbt); }

    @Override
    public CompoundNBT write(CompoundNBT nbt)
    { super.write(nbt); writenbt(nbt); return nbt; }

    @Override
    public CompoundNBT getUpdateTag()
    { CompoundNBT nbt = super.getUpdateTag(); writenbt(nbt, true); return nbt; }

    @Override
    @Nullable
    public SUpdateTileEntityPacket getUpdatePacket()
    { return new SUpdateTileEntityPacket(pos, 1, getUpdateTag()); }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) // on client
    { readnbt(pkt.getNbtCompound()); super.onDataPacket(net, pkt); }

    @Override
    public void handleUpdateTag(BlockState state, CompoundNBT tag) // on client
    { read(state, tag); }

    @Override
    public void onServerPacketReceived(CompoundNBT nbt)
    { readnbt(nbt); }

    @Override
    public void onClientPacketReceived(PlayerEntity player, CompoundNBT nbt)
    {}

    @OnlyIn(Dist.CLIENT)
    public double getMaxRenderDistanceSquared()
    { return 64; }

    /// -------------------------------------------------------------------------------------------

    public boolean sync(boolean schedule)
    {
      if(world.isRemote()) return true;
      markDirty();
      if(schedule && (!getWorld().getPendingBlockTicks().isTickScheduled(getPos(), ModContent.TRACK_BLOCK))) {
        getWorld().getPendingBlockTicks().scheduleTick(getPos(), ModContent.TRACK_BLOCK, 1);
      } else {
        Networking.PacketTileNotifyServerToClient.sendToPlayers(this, writenbt(new CompoundNBT(), true));
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
      p = ((p <= 0) || (!(getWorld().getBlockState(getPos().offset(own_side)).isIn(Blocks.REDSTONE_WIRE)))) ? p : (p-1);
      if(trace_) Auxiliaries.logWarn(String.format("POWR: %12s(%s)==%d", posstr(getPos()), redstone_side.toString(), p));
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

    public void toggle_trace(@Nullable PlayerEntity player)
    { trace_ = !trace_; if(player!=null) Auxiliaries.playerChatMessage(player, "Trace: " + trace_); }

    public int handleActivation(BlockPos pos, PlayerEntity player, Hand hand, Direction clicked_face, Vector3d hitvec, boolean remove_only)
    {
      final ItemStack used_stack = player.getHeldItem(hand);
      if((!used_stack.isEmpty()) && (used_stack.getItem()!=Items.REDSTONE) && (used_stack.getItem()!=ModContent.PEN_ITEM)) return 0;
      long flip_mask;
      final Direction face = clicked_face.getOpposite();
      {
        Vector3d hit = hitvec.subtract(Vector3d.copyCentered(pos));
        switch(clicked_face) {
          case WEST:  case EAST:  hit = hit.mul(0, 1, 1); break;
          case SOUTH: case NORTH: hit = hit.mul(1, 1, 0); break;
          default:                hit = hit.mul(1, 0, 1); break;
        }
        final Direction dir = Direction.getFacingFromVector(hit.getX(), hit.getY(), hit.getZ());
        final int wire_flags = getWireFlags();
        final int connection_flags = getConnectionFlags();
        if((hit.length() < 0.12) && ((!remove_only) || (connection_flags!=0))) {
          // Centre connection
          if(getWireFlags()!=0) {
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
      if(material_use != 0) {
        updateConnections();
        updateAllPowerValuesFromAdjacent();
        sync(true);
        for(int i=0; (i<defs.STATE_FLAG_WIR_COUNT) && (flip_mask!=0); ++i) {
          long bit = 1L<<i;
          if((flip_mask & bit) == 0) continue;
          flip_mask &= ~bit;
          Tuple<Direction,Direction> side_dir = defs.connections.getWireBitSideAndDirection(bit);
          TileEntity te = getWorld().getTileEntity(getPos().offset(side_dir.getB()));
          if(te instanceof TrackTileEntity) {
            ((TrackTileEntity)te).updateConnections();
          } else {
            te = getWorld().getTileEntity(getPos().offset(side_dir.getA()).offset(side_dir.getB()));
            if(te instanceof TrackTileEntity) {
              ((TrackTileEntity)te).updateConnections();
            }
          }
        }
      }
      return material_use;
    }

    public Map<BlockPos,BlockPos> updateAllPowerValuesFromAdjacent()
    {
      final Map<BlockPos,BlockPos> all_change_notifications = new HashMap<>();
      for(Direction side: Direction.values()) {
        BlockPos npos = getPos().offset(side);
        BlockState nstate = world.getBlockState(npos);
        Map<BlockPos,BlockPos> change_notifications = handleNeighborChanged(nstate.getBlock(), npos);
        change_notifications.forEach((key, value) -> all_change_notifications.putIfAbsent(key, value));
      }
      return all_change_notifications;
    }

    private void spawnRedsoneItems(int count)
    {
      if(count <= 0) return;
      final ItemEntity e = new ItemEntity(getWorld(), getPos().getX()+.5, getPos().getY()+.5, getPos().getZ()+.5);
      e.setDefaultPickupDelay();
      e.setItem(new ItemStack(Items.REDSTONE, count));
      e.setMotion(new Vector3d(getWorld().getRandom().nextDouble()-.5, getWorld().getRandom().nextDouble()-.5, getWorld().getRandom().nextDouble()).scale(0.1));
      getWorld().addEntity(e);
    }

    private RedstoneTrackBlock getBlock()
    { return ModContent.TRACK_BLOCK; }

    public boolean handlePostPlacement(Direction facing, BlockState facingState, BlockPos fromPos)
    {
      if(!RedstoneTrackBlock.canBePlacedOnFace(facingState, getWorld(), fromPos, facing.getOpposite())) {
        final long to_remove = defs.connections.getAllElementsOnFace(facing);
        final long new_flags = (state_flags_ & ~to_remove);
        if(new_flags != state_flags_) {
          //if(trace_) Auxiliaries.logWarn(String.format("PPLC: %s (<-%s.%s)", posstr(getPos()), posstr(fromPos), facingState.getBlock().getRegistryName().getPath()));
          int count = getRedstoneDustCount();
          state_flags_ = new_flags;
          count -= getRedstoneDustCount();
          spawnRedsoneItems(count);
          updateConnections();
          handleNeighborChanged(facingState.getBlock(), fromPos);
        } else if(block_change_tracking_[facing.getIndex()] != facingState.getBlock()) {
          block_change_tracking_[facing.getIndex()] = facingState.getBlock();
          updateConnections();
          handleNeighborChanged(facingState.getBlock(), fromPos);
        }
      }
      return (getWireFlags()!=0);
    }

    private int getNonWireSignal(World world, BlockPos pos, Direction redstone_side)
    {
      // According to world.getRedstonePower():
      getBlock().disablePower(true);
      final BlockState state = world.getBlockState(pos);
      int p = (!state.isIn(Blocks.REDSTONE_WIRE) && (!state.isIn(getBlock()))) ? state.getWeakPower(world, pos, redstone_side) : 0;
      //if(trace_) Auxiliaries.logWarn(String.format("GETNWS from [%s @ %s] = %dw", posstr(getPos()), redstone_side, p));
      if(!state.shouldCheckWeakPower(world, pos, redstone_side)) { getBlock().disablePower(false); return p; }
      // According to world.getStrongPower():
      for(Direction rs_side: Direction.values()) {
        final BlockPos side_pos = pos.offset(rs_side);
        final BlockState side_state = world.getBlockState(side_pos);
        if(side_state.isIn(Blocks.REDSTONE_WIRE) || side_state.isIn(getBlock())) continue;
        final int p_in = side_state.getStrongPower(world, side_pos, rs_side);
        if(p_in > p) {
          p = p_in;
          if(p >= 15) break;
        }
      }
      getBlock().disablePower(false);
      //if(trace_) Auxiliaries.logWarn(String.format("GETNWS from [%s @ %s] = %dS", posstr(getPos()), redstone_side, p));
      return p;
    }

    public Map<BlockPos,BlockPos> handleNeighborChanged(Block fromBlock, BlockPos fromPos)
    {
      final Map<BlockPos,BlockPos> change_notifications = new HashMap<>();
      boolean power_changed = false;
      for(TrackNet net: nets_) {
        if(!net.neighbour_positions.contains(fromPos)) continue;
        //if(trace_) Auxiliaries.logWarn(String.format("CHNOT: (%s) from [%s]", posstr(getPos()), posstr(fromPos)));
        int pmax = 0;
        for(int i = 0; i<net.neighbour_positions.size(); ++i) {
          final BlockPos ext_pos = net.neighbour_positions.get(i);
          change_notifications.put(ext_pos, getPos());
          if(pmax < 15) {
            final Direction ext_side = net.neighbour_sides.get(i);
            final BlockState ext_state = world.getBlockState(ext_pos);
            if(ext_state.isIn(Blocks.REDSTONE_WIRE)) {
              final int p_vanilla_wire = Math.max(0, ext_state.get(RedstoneWireBlock.POWER)-1);
              pmax = Math.max(pmax, p_vanilla_wire);
            } else if(ext_state.isIn(getBlock())) {
              final int p_track = RedstoneTrackBlock.tile(getWorld(), ext_pos).map(te->Math.max(0, te.getSidePower(ext_side)-1)).orElse(0);
              pmax = Math.max(pmax, p_track);
            } else {
              final Direction eside = ext_side.getOpposite();
              final int p_nowire = getNonWireSignal(getWorld(), ext_pos, eside);
              pmax = Math.max(pmax, p_nowire);
              if((!ext_state.canProvidePower()) && (p_nowire == 0)) {
                if(ext_side!=Direction.DOWN) change_notifications.putIfAbsent(ext_pos.offset(Direction.DOWN), ext_pos);
                if(ext_side!=Direction.UP) change_notifications.putIfAbsent(ext_pos.offset(Direction.UP), ext_pos);
                if(ext_side!=Direction.NORTH) change_notifications.putIfAbsent(ext_pos.offset(Direction.NORTH), ext_pos);
                if(ext_side!=Direction.SOUTH) change_notifications.putIfAbsent(ext_pos.offset(Direction.SOUTH), ext_pos);
                if(ext_side!=Direction.EAST) change_notifications.putIfAbsent(ext_pos.offset(Direction.EAST), ext_pos);
                if(ext_side!=Direction.WEST) change_notifications.putIfAbsent(ext_pos.offset(Direction.WEST), ext_pos);
              }
            }
          }
        }
        if(net.power != pmax) {
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
          Auxiliaries.logWarn(String.format("CHNOT: (%s) updates: [%s]", posstr(getPos()), change_notifications.entrySet().stream().map(kv-> posstr(kv.getKey())+">"+posstr(kv.getValue())).collect(Collectors.joining(" ; "))));
        }
        sync(true);
        return change_notifications;
      } else {
        return Collections.emptyMap();
      }
    }

    private final String posstr(BlockPos pos)
    { return "[" +pos.getX()+ "," +pos.getY()+ "," +pos.getZ()+ "]"; }

    @SuppressWarnings("deprecation")
    private boolean isRedstoneInsulator(BlockState state)
    {
      // if(state.isAir()) return false;
      if(state.getMaterial() == Material.GLASS) return true;
      return false;
    }

    private void updateConnections()
    { updateConnections(1); }

    private void updateConnections(int recursion_left)
    {
      nets_.clear();
      final Set<TrackTileEntity> track_connection_updates = new HashSet<>();
      final long internal_connected_sides[] = {0,0,0,0,0,0};
      final long external_connected_routes[] = {0,0,0,0,0,0};
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
        if(trace_) Auxiliaries.logWarn(String.format("UCON %s AA: ext:%08x | int:[%08x %08x %08x %08x %08x %08x]", posstr(getPos()), external_connection_flags, internal_connected_sides[0], internal_connected_sides[1], internal_connected_sides[2], internal_connected_sides[3], internal_connected_sides[4], internal_connected_sides[5]));
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
        if(trace_) Auxiliaries.logWarn(String.format("UCON: %s B1: ext:%08x | int:[%08x %08x %08x %08x %08x %08x]", posstr(getPos()), external_connection_flags, internal_connected_sides[0], internal_connected_sides[1], internal_connected_sides[2], internal_connected_sides[3], internal_connected_sides[4], internal_connected_sides[5]));
        if(trace_) Auxiliaries.logWarn(String.format("UCON: %s B2: ext:%08x | ext:[%08x %08x %08x %08x %08x %08x]", posstr(getPos()), external_connection_flags, external_connected_routes[0], external_connected_routes[1], external_connected_routes[2], external_connected_routes[3], external_connected_routes[4], external_connected_routes[5]));
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
                final BlockPos wire_pos = getPos().offset(tdir);
                final BlockState wire_state = getWorld().getBlockState(wire_pos);
                boolean diagonal_check = false;
                if(wire_state.isIn(getBlock())) {
                  // adjacent track
                  long adjacent_mask = defs.connections.getWireBit(tsid, tdir.getOpposite());
                  TrackTileEntity adj_te = RedstoneTrackBlock.tile(getWorld(), wire_pos).orElse(null);
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
                if((!diagonal_check) && wire_state.isIn(Blocks.REDSTONE_WIRE)) {
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
                if((!diagonal_check) && wire_state.canProvidePower()) {
                  // adjacent power block
                  block_positions.add(wire_pos);
                  block_sides.add(tdir.getOpposite()); // real face.
                  internal_sides.add(side);
                  power_sides.add(tdir);
                  continue;
                }
                // diagonal track
                {
                  final BlockPos track_pos = wire_pos.offset(tsid);
                  final BlockState track_state = getWorld().getBlockState(track_pos);
                  if(track_state.isIn(getBlock())) {
                    long adjacent_mask = defs.connections.getWireBit(tdir.getOpposite(), tsid.getOpposite());
                    TrackTileEntity adj_te = RedstoneTrackBlock.tile(getWorld(), track_pos).orElse(null);
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
                if(!isRedstoneInsulator(wire_state)) {
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
              final BlockPos bulk_pos = getPos().offset(side);
              final BlockState bulk_state = getWorld().getBlockState(bulk_pos);
              if(isRedstoneInsulator(bulk_state)) continue;
              block_positions.add(bulk_pos);
              block_sides.add(side.getOpposite()); // NOT the redstone side, the real face.
              internal_sides.add(side);
              power_sides.add(side);
            }
          }
          // Update net
          if(!block_positions.isEmpty()) {
            nets_.add(new TrackNet(block_positions, block_sides, new ArrayList<>(internal_sides), new ArrayList<>(power_sides)));
            used_sides.addAll(internal_sides);
          }
        }
        Arrays.stream(Direction.values()).filter(side->!used_sides.contains(side)).forEach(side->setSidePower(side, 0));
      }
      markDirty();
      {
        final String poss = posstr(getPos());
        for(TrackNet net:nets_) {
          final List<String> ss = new ArrayList<>();
          for(int i = 0; i<net.neighbour_positions.size(); ++i) ss.add(posstr(net.neighbour_positions.get(i)) + ":" + net.neighbour_sides.get(i).toString());
          String int_sides = net.internal_sides.stream().map(Direction::toString).collect(Collectors.joining(","));
          String pwr_sides = net.power_sides.stream().map(Direction::toString).collect(Collectors.joining(","));
          if(trace_) Auxiliaries.logWarn(String.format("UCON %s: adj:%s | ints:%s | pwrs:%s", poss, String.join(", ", ss), int_sides, pwr_sides));
        }
      }
      if(recursion_left > 0) {
        for(TrackTileEntity te:track_connection_updates) {
          if(trace_) Auxiliaries.logWarn(String.format("UCON %s: UPDATE NET OF %s", posstr(getPos()), posstr(te.getPos())));
          te.updateConnections(recursion_left-1);
        }
      }
    }
  }

}
