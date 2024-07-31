/*
 * @file Lever.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.blocks;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import wile.redstonepen.libmc.Auxiliaries;

import java.util.List;


@SuppressWarnings("deprecation")
public class BasicLever
{
  //--------------------------------------------------------------------------------------------------------------------
  // BasicLeverBlock
  //--------------------------------------------------------------------------------------------------------------------

  public static class BasicLeverBlock extends net.minecraft.world.level.block.LeverBlock
  {
    public record Config(float sound_pitch_unpowered, float sound_pitch_powered) {}

    public final Config config;

    public BasicLeverBlock(Config conf, BlockBehaviour.Properties properties)
    { super(properties); config = conf; }

    @Override
    @Environment(EnvType.CLIENT)
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag)
    { Auxiliaries.Tooltip.addInformation(stack, ctx, tooltip, flag, true); }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult brh)
    {
      if(world.isClientSide) {
        final BlockState new_state = state.cycle(POWERED);
        if(new_state.getValue(POWERED)) makeParticle(new_state, world, pos, 1.0f);
        return InteractionResult.SUCCESS;
      } else {
        final BlockState new_state = state.cycle(POWERED);
        world.setBlock(pos, new_state, 1|2);
        world.updateNeighborsAt(pos, this);
        world.updateNeighborsAt(pos.relative(LeverBlock.getConnectedDirection(new_state).getOpposite()), this);
        world.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, new_state.getValue(POWERED) ? config.sound_pitch_powered() : config.sound_pitch_unpowered());
        world.gameEvent(player, new_state.getValue(POWERED) ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
        return InteractionResult.CONSUME;
      }
    }

    private static void makeParticle(BlockState state, LevelAccessor world, BlockPos pos, float f)
    {
      for(int i=0; i<2; ++i) {
        final Vec3 vpos = Vec3.atCenterOf(pos)
          .add(Vec3.atBottomCenterOf(state.getValue(FACING).getOpposite().getNormal()).scale(0.1))
          .add(Vec3.atLowerCornerOf(net.minecraft.world.level.block.LeverBlock.getConnectedDirection(state).getOpposite().getNormal()).scale(0.2));
        world.addParticle(new DustParticleOptions(DustParticleOptions.REDSTONE_PARTICLE_COLOR, f), vpos.x(), vpos.y(), vpos.z(), 0.0, 0.0, 0.0);
      }
    }
  }

}
