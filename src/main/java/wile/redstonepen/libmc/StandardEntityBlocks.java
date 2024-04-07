/*
 * @file StandardEntityBlocks.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Common functionality class for blocks with block entities.
 */
package wile.redstonepen.libmc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEventListener;

import javax.annotation.Nullable;


public class StandardEntityBlocks
{
  public interface IStandardEntityBlock<ET extends StandardBlockEntity> extends EntityBlock
  {

    default boolean isBlockEntityTicking(Level world, BlockState state)
    { return false; }

    default InteractionResult useOpenGui(BlockState state, Level world, BlockPos pos, Player player)
    {
      if(world.isClientSide()) return InteractionResult.SUCCESS;
      final BlockEntity te = world.getBlockEntity(pos);
      if(!(te instanceof MenuProvider)) return InteractionResult.FAIL;
      player.openMenu((MenuProvider)te);
      return InteractionResult.CONSUME;
    }

    @Override
    @Nullable
    default BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
      BlockEntityType<?> tet = Registries.getBlockEntityTypeOfBlock(state.getBlock());
      return (tet==null) ? null : tet.create(pos, state);
    }

    @Override
    @Nullable
    default <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> te_type)
    { return (world.isClientSide || (!isBlockEntityTicking(world, state))) ? (null) : ((Level w, BlockPos p, BlockState s, T te) -> ((StandardBlockEntity)te).tick()); }

    @Override
    @Nullable
    default <T extends BlockEntity> GameEventListener getListener(ServerLevel world, T te)
    { return null; }
  }

  public static abstract class StandardBlockEntity extends BlockEntity
  {
    public StandardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state)
    { super(type, pos, state); }

    public void tick()
    {}

    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) // on client
    {}

    public void handleUpdateTag(CompoundTag tag) // on client
    {}
  }

}
