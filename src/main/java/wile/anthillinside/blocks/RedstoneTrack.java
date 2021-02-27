/*
 * @file Hive.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.blocks;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.schedule.Activity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.pathfinding.PathType;
import net.minecraft.state.BooleanProperty;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.state.StateContainer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.items.IItemHandler;
import wile.redstonepen.ModContent;
import wile.redstonepen.libmc.blocks.StandardBlocks;
import wile.redstonepen.libmc.detail.Auxiliaries;
import wile.redstonepen.libmc.detail.Inventories;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;


public class RedAntTrail
{
  private static double speed_modifier = 1.0;

  public static void on_config(int speed_percent)
  {
    speed_modifier = ((double)MathHelper.clamp((speed_percent), 25, 200))/100;
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Block
  //--------------------------------------------------------------------------------------------------------------------

  public static class RedAntTrailBlock extends StandardBlocks.HorizontalWaterLoggable implements StandardBlocks.IBlockItemFactory
  {
    public static final BooleanProperty FRONT = BooleanProperty.create("front");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty UP = BooleanProperty.create("up");

    public RedAntTrailBlock(long config, Block.Properties builder)
    {
      super(config, builder, (states)->{
        final HashMap<BlockState,VoxelShape> shapes = new HashMap<>();
        final AxisAlignedBB base_aabb = Auxiliaries.getPixeledAABB(0,0,0,16,0.2,16);
        final AxisAlignedBB up_aabb   = Auxiliaries.getPixeledAABB(0,0,0,16,16,0.2);
        for(BlockState state:states) {
          final Direction facing = state.get(HORIZONTAL_FACING);
          VoxelShape shape = VoxelShapes.empty();
          if(state.get(UP)) {
            shape = VoxelShapes.combine(shape, VoxelShapes.create(Auxiliaries.getRotatedAABB(up_aabb, facing, true)), IBooleanFunction.OR);
          }
          if(state.get(FRONT) || (!state.get(UP))) {
            shape = VoxelShapes.combine(shape, VoxelShapes.create(Auxiliaries.getRotatedAABB(base_aabb, facing, true)), IBooleanFunction.OR);
          }
          shapes.putIfAbsent(state, shape);
        }
        return shapes;
      });
      setDefaultState(super.getDefaultState().with(FRONT, true).with(UP, false).with(LEFT, false).with(RIGHT, false));
    }

    @Override
    public BlockItem getBlockItem(Block block, Item.Properties builder)
    { return ModContent.ANTS_ITEM; }

    @Override
    public boolean hasDynamicDropList()
    { return true; }

    @Override
    public Item asItem()
    { return ModContent.ANTS_ITEM; }

    @Override
    public List<ItemStack> dropList(BlockState state, World world, final TileEntity te, boolean explosion)
    { return Collections.singletonList(new ItemStack(asItem())); }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    { super.fillStateContainer(builder); builder.add(FRONT, LEFT, RIGHT, UP); }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockItemUseContext context)
    {
      BlockState state = super.getStateForPlacement(context);
      if(state == null) return state;
      if((!state.get(UP)) && (context.getFace().getAxis().isVertical()) && (!Block.hasSolidSideOnTop(context.getWorld(), context.getPos().down()))) return null;
      return updatedState(state, context.getWorld(), context.getPos());
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
    {
      final Direction block_facing = state.get(HORIZONTAL_FACING);
      for(Direction facing: Direction.values()) {
        if(!facing.getAxis().isHorizontal()) continue;
        if(facing == block_facing) continue;
        final BlockPos diagonal_pos = pos.offset(facing).down();
        final BlockState diagonal_state = world.getBlockState(diagonal_pos);
        if(!diagonal_state.isIn(this)) continue;
        if(diagonal_state.get(UP)) continue;
        final Direction diagonal_facing = diagonal_state.get(HORIZONTAL_FACING);
        if(diagonal_facing != facing.getOpposite()) continue;
        world.setBlockState(diagonal_pos, diagonal_state.with(UP, true), 2);
      }
    }

    @Override
    public BlockState updatePostPlacement(BlockState state, Direction facing, BlockState facingState, IWorld world, BlockPos pos, BlockPos facingPos)
    {
      if(!(world instanceof World)) return state;
      if(isValidPosition(state, world, pos)) return updatedState(state, world, pos);
      return Blocks.AIR.getDefaultState();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isValidPosition(BlockState state, IWorldReader world, BlockPos pos)
    {
      if(Block.doesSideFillSquare(world.getBlockState(pos.down()).getShape(world, pos.down()), Direction.UP)) return true;
      if(!state.get(UP)) return false;
      Direction facing = state.get(HORIZONTAL_FACING);
      if(!Block.doesSideFillSquare(world.getBlockState(pos.offset(facing)).getShape(world, pos.offset(facing)), facing.getOpposite())) return false;
      return true;
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult rtr)
    {
      Direction dir = rtr.getFace().getOpposite();
      return world.getBlockState(pos.offset(dir)).onBlockActivated(world, player, hand, new BlockRayTraceResult(
        rtr.getHitVec(), rtr.getFace(), pos.offset(dir), false
      ));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity)
    {
      if(entity instanceof ItemEntity) {
        moveEntity(state, world, pos, entity);
        if(state.get(UP) && (!world.getPendingBlockTicks().isTickScheduled(pos, this))) {
          world.getPendingBlockTicks().scheduleTick(pos, this, 60);
        }
      } else if(entity instanceof LivingEntity) {
        itchEntity(state, world, pos, entity);
      }
    }

    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos pos, Random rand)
    {
      if(!state.get(UP)) return;
      final BlockState st = world.getBlockState(pos.up());
      if(st==null || st.isIn(this)) return;
      final List<ItemEntity> entities = world.getEntitiesWithinAABB(ItemEntity.class, new AxisAlignedBB(pos.up()).expand(0,-0.2,0), Entity::isAlive);
      final Vector3d v = Vector3d.copy(state.get(HORIZONTAL_FACING).getDirectionVec()).add(0,1,0).scale(0.1);
      for(ItemEntity entity:entities) entity.setMotion(v);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean unused)
    { updatedState(state, world, pos); }

    @Override
    public boolean shouldCheckWeakPower(BlockState state, IWorldReader world, BlockPos pos, Direction side)
    { return false; }

    @Override
    public boolean allowsMovement(BlockState state, IBlockReader world, BlockPos pos, PathType type)
    { return (!state.get(UP)) || super.allowsMovement(state, world, pos, type); }

    //------------------------------------------------------------------------------------------------------

    public BlockState updatedState(@Nullable BlockState state, IWorld world, BlockPos pos)
    {
      if((state == null) || (!(world instanceof World))) return state;
      final Direction facing = state.get(HORIZONTAL_FACING);
      boolean down_solid = Block.hasSolidSideOnTop(world, pos.down());
      boolean up_is_cube = world.getBlockState(pos.up()).isNormalCube(world, pos.up());
      final boolean up = doesSideFillSquare(world.getBlockState(pos.offset(facing)).getShape(world, pos.offset(facing)), facing.getOpposite())
        && ((!down_solid) || (world.getBlockState(pos.up()).isIn(this)) || ((!up_is_cube) && (world.getBlockState(pos.offset(facing).up()).isIn(this))));
      boolean left = false, right = false;
      boolean front = down_solid;
      if(((World)world).isBlockPowered(pos)) {
        {
          final BlockState right_state = world.getBlockState(pos.offset(facing.rotateY()));
          if(right_state.isIn(this)) {
            if(right_state.get(HORIZONTAL_FACING) == facing.rotateY()) right = true;
          }
        }
        {
          final BlockState left_state = world.getBlockState(pos.offset(facing.rotateYCCW()));
          if(left_state.isIn(this)) {
            if(left_state.get(HORIZONTAL_FACING) == facing.rotateYCCW()) left = true;
          }
        }
        if(!right && !left) {
          front = false;
        }
      } else if(!right && !left && !up) {
        front = true;
      }
      state = state.with(FRONT, front).with(RIGHT, right).with(LEFT, left).with(UP, up);
      return state;
    }

    public void moveEntity(BlockState state, World world, BlockPos pos, Entity any_entity)
    {
      if((!any_entity.isAlive()) || (!(any_entity instanceof ItemEntity))) return;
      final ItemEntity entity = (ItemEntity)any_entity;
      if(entity.getItem().isEmpty() || entity.getItem().getItem() == ModContent.ANTS_ITEM) return;
      final boolean up = state.get(UP);
      if(!up && !entity.isOnGround()) return;
      double speed = 7e-2 * speed_modifier;
      final Vector3d dp = entity.getPositionVec().subtract(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5).scale(2);
      if((!up) && (dp.y > 0)) speed *= 0.2;
      boolean outgoing = false, check_insertion_front = false, check_insertion_up = false;
      final boolean front = state.get(FRONT);
      final Direction block_facing = state.get(HORIZONTAL_FACING);
      Vector3d motion = Vector3d.copy(block_facing.getDirectionVec());
      if(state.get(RIGHT)) {
        final Direction facing_right = block_facing.rotateY();
        final BlockState right_state = world.getBlockState(pos.offset(facing_right));
        if(right_state.isIn(this)) {
          final Direction dir = right_state.get(HORIZONTAL_FACING);
          if(dir == facing_right) {
            motion = Vector3d.copy(facing_right.getDirectionVec());
            outgoing = true;
          }
        }
      } else if(state.get(LEFT)) {
        final Direction facing_left  = block_facing.rotateYCCW();
        final BlockState left_state = world.getBlockState(pos.offset(facing_left));
        if(left_state.isIn(this)) {
          final Direction dir = left_state.get(HORIZONTAL_FACING);
          if(dir == facing_left) {
            motion = Vector3d.copy(facing_left.getDirectionVec());
            outgoing = true;
          }
        }
      }
      {
        if(!outgoing && !front && !up) {
          motion = motion.scale(0);
        } else {
          if(!outgoing) {
            Vector3d centering_motion = new Vector3d((block_facing.getAxis()==Axis.X ? 0 : -0.2*Math.signum(dp.x)), 0, (block_facing.getAxis()==Axis.Z ? 0 : -0.1*Math.signum(dp.z)));
            if(up) centering_motion.scale(2);
            motion = motion.add(centering_motion);
          }
          final BlockState ahead_state = world.getBlockState(pos.offset(block_facing));
          if(!outgoing && ahead_state.isIn(this)) {
            final Direction dir = ahead_state.get(HORIZONTAL_FACING);
            if(dir == block_facing) {
              motion = motion.scale(2);
            } else if(dir == block_facing.getOpposite()) {
              motion = motion.scale(0.5);
            } else if(ahead_state.get(UP)) {
              motion = motion.scale(2);
            }
          }
          final double progress = dp.getCoordinate(block_facing.getAxis()) * Vector3d.copy(block_facing.getDirectionVec()).getCoordinate(block_facing.getAxis());
          double y_speed = -0.1 * Math.min(dp.y, 0.5);
          if(!up) {
            if((progress > 0.7) && front) check_insertion_front = true;
          } else {
            if(front && (dp.y < 0.3) && (progress < 0.6)) {
              y_speed = 0.08;
            } else if((progress > 0.7) && (world.getBlockState(pos.up().offset(block_facing)).isIn(this))) {
              motion = motion.scale(1.2);
              y_speed = 0.14;
            } else {
              y_speed = 0.1;
            }
            if((dp.y >= 0.4) && (!world.getBlockState(pos.up()).isIn(this))) {
              check_insertion_up = true;
            }
          }
          if(motion.y < -0.1) motion = new Vector3d(motion.x, -0.1, motion.z);
          motion = motion.scale(speed).add(0, y_speed, 0);
        }
      }
      if((check_insertion_front || check_insertion_up) && (!world.isRemote())) {
        if(!entity.getItem().isEmpty()){
          final Direction insertion_facing = check_insertion_up ? Direction.UP : block_facing;
          final IItemHandler ih = Inventories.itemhandler(world, pos.offset(insertion_facing), insertion_facing.getOpposite());
          if(ih != null) {
            ItemStack stack = entity.getItem().copy();
            final ItemStack remaining = Inventories.insert(ih, stack, false);
            if(remaining.getCount() < stack.getCount()) {
              if(stack.isEmpty()) {
                entity.remove();
              } else {
                entity.setItem(remaining);
              }
            }
          }
        }
      }
      if(state.get(WATERLOGGED)) motion.add(0,-0.1,0);
      motion = entity.getMotion().scale(0.5).add(motion);
      entity.setMotion(motion);
    }

    public void itchEntity(BlockState state, World world, BlockPos pos, Entity entity)
    {
      if((world.getRandom().nextDouble() > 8e-3) || (!entity.isAlive()) || (!entity.isOnGround())
         || (world.isRemote()) || (entity.isSneaking()) || (!entity.isNonBoss())
         || (!(entity instanceof LivingEntity))
      ) {
        return;
      }
      if(entity instanceof MonsterEntity) {
        entity.attackEntityFrom(DamageSource.CACTUS, 2f);
      } else if(entity instanceof PlayerEntity) {
        if(world.getRandom().nextDouble() > 1e-1) return;
        entity.attackEntityFrom(DamageSource.CACTUS, 0.1f);
      } else {
        entity.attackEntityFrom(DamageSource.CACTUS, 0.1f);
        if(entity instanceof VillagerEntity) {
          ((VillagerEntity)entity).getBrain().switchTo(Activity.PANIC);
        } else if(entity instanceof AnimalEntity) {
          ((AnimalEntity)entity).getBrain().switchTo(Activity.PANIC);
        }
      }
    }
  }

}
