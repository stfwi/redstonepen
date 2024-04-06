/*
 * @file Lever.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import javax.annotation.Nullable;
import wile.redstonepen.libmc.Auxiliaries;

import java.util.List;


@SuppressWarnings("deprecation")
public class BasicButton
{
  //--------------------------------------------------------------------------------------------------------------------
  // DirectedComponentBlock
  //--------------------------------------------------------------------------------------------------------------------

  public static class BasicButtonBlock extends net.minecraft.world.level.block.ButtonBlock
  {
    public record Config(float sound_pitch_unpowered, float sound_pitch_powered, int active_time) {}

    public final Config config;

    public BasicButtonBlock(Config conf, BlockBehaviour.Properties properties)
    { super(false,properties); config = conf; }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter world, List<Component> tooltip, TooltipFlag flag)
    { Auxiliaries.Tooltip.addInformation(stack, world, tooltip, flag, true); }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult bhr)
    {
      if(state.getValue(POWERED)) return InteractionResult.CONSUME;
      this.press(state, world, pos);
      this.playSound(player, world, pos, true);
      world.gameEvent(player, GameEvent.BLOCK_ACTIVATE, pos);
      if(world.isClientSide) makeParticle(state, world, pos, 1.0f);
      return InteractionResult.sidedSuccess(world.isClientSide);
    }

    @Override
    protected void playSound(@Nullable Player player, LevelAccessor world, BlockPos pos, boolean on)
    {
      world.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, on ? config.sound_pitch_powered() : config.sound_pitch_unpowered());
    }

    private static void makeParticle(BlockState state, LevelAccessor world, BlockPos pos, float f)
    {
      for(int i=0; i<3; ++i) {
        final Vec3 vpos = Vec3.atCenterOf(pos)
          .add(Vec3.atBottomCenterOf(state.getValue(FACING).getOpposite().getNormal()).scale(0.1))
          .add(Vec3.atLowerCornerOf(net.minecraft.world.level.block.LeverBlock.getConnectedDirection(state).getOpposite().getNormal()).scale(0.4));
        world.addParticle(new DustParticleOptions(DustParticleOptions.REDSTONE_PARTICLE_COLOR, f), vpos.x(), vpos.y(), vpos.z(), 0.0, 0.0, 0.0);
      }
    }

    protected SoundEvent getSound(boolean sensitive)
    { return SoundEvents.LEVER_CLICK; }

    public void press(BlockState state, Level world, BlockPos pos) {
      world.setBlock(pos, state.setValue(POWERED, true), 3);
      this.updateNeighbours(state, world, pos);
      world.scheduleTick(pos, this, config.active_time);
    }

    private void updateNeighbours(BlockState state, Level world, BlockPos pos)
    {
      world.updateNeighborsAt(pos, this);
      world.updateNeighborsAt(pos.relative(getConnectedDirection(state).getOpposite()), this);
    }

    private void checkPressed(BlockState state, Level world, BlockPos pos)
    {
      final List<? extends Entity> arrows = world.getEntitiesOfClass(AbstractArrow.class, state.getShape(world, pos).bounds().move(pos));
      final boolean hit = !arrows.isEmpty();
      if (hit != state.getValue(POWERED)) {
        world.setBlock(pos, state.setValue(POWERED, hit), 3);
        updateNeighbours(state, world, pos);
        playSound(null, world, pos, hit);
        world.gameEvent(arrows.stream().findFirst().orElse(null), hit ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
      }
      if(hit) {
        world.scheduleTick(new BlockPos(pos), this, config.active_time);
      }
    }

  }

}
