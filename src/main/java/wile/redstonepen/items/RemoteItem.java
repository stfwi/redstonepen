/*
 * @file RemoteItem.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.items;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import wile.redstonepen.ModConstants;
import wile.redstonepen.blocks.ControlBox;
import wile.redstonepen.libmc.Auxiliaries;
import wile.redstonepen.libmc.Overlay;
import wile.redstonepen.libmc.StandardItems;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;


@SuppressWarnings("deprecation")
public class RemoteItem extends StandardItems.BaseItem
{
  public RemoteItem(Item.Properties properties)
  { super(properties); }

  //------------------------------------------------------------------------------------------------------------------

  @Override
  @OnlyIn(Dist.CLIENT)
  public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag)
  {
    final var data = getRemoteData(stack).orElse(null);
    if(data != null) {
      tooltip.add(Auxiliaries.localizable("item." + ModConstants.MODID + ".remote.tooltip.linkedto", data.pos.getX(), data.pos.getY(), data.pos.getZ(), Component.translatable(data.name)));
    } else {
      tooltip.add(Auxiliaries.localizable("item." + ModConstants.MODID + ".remote.tooltip.notlinked"));
    }
    Auxiliaries.Tooltip.addInformation(stack, ctx, tooltip, flag, true);
  }

  @Override
  public boolean doesSneakBypassUse(ItemStack stack, LevelReader world, BlockPos pos, Player player)
  { return false; }

  @Override
  public boolean isBarVisible(ItemStack stack)
  { return false; }

  @Override
  public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player player)
  {
    final ItemStack stack = (player.getMainHandItem().getItem() instanceof RemoteItem) ? player.getMainHandItem() : player.getOffhandItem();
    attack(stack, pos, player);
    return false;
  }

  @Override
  public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player)
  { attack(stack, pos, player); return false; }

  @Override
  public float getDestroySpeed(ItemStack stack, BlockState state)
  { return 10000f; }

  @Override
  public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand)
  {
    if(world.isClientSide) return InteractionResultHolder.success(player.getItemInHand(hand));
    onTriggerRemoteLink((ServerLevel)world, (ServerPlayer)player, player.getItemInHand(hand));
    return InteractionResultHolder.fail(player.getItemInHand(hand));
  }

  @Override
  public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context)
  {
    if(context.getLevel().isClientSide) return InteractionResult.SUCCESS;
    onTriggerRemoteLink((ServerLevel)context.getLevel(), (ServerPlayer)context.getPlayer(), stack);
    return InteractionResult.CONSUME;
  }

  //------------------------------------------------------------------------------------------------------------------

  private void onTriggerRemoteLink(ServerLevel world, ServerPlayer player, ItemStack stack)
  {
    final var data = getRemoteData(stack).orElse(null);
    if(data == null) return;
    final BiConsumer<SoundEvent, Float> sound = (event, pitch)->world.playSound(null, player.blockPosition(), event, SoundSource.PLAYERS, 0.25f, pitch);
    final Runnable fail = ()->sound.accept(SoundEvents.ENDERMAN_HURT, 1.8f);
    final BlockPos pos = data.pos;
    if(!world.isLoaded(pos)) { fail.run(); return; }
    final BlockState state = world.getBlockState(pos);
    if(!state.hasProperty(BlockStateProperties.POWERED)) { fail.run(); return; }
    final Block block = state.getBlock();
    final boolean powered = state.getValue(BlockStateProperties.POWERED);
    if(block instanceof ButtonBlock button) {
      if(powered) return;
      button.press(state, world, pos, null);
      sound.accept(SoundEvents.STONE_BUTTON_CLICK_ON, 1.7f);
    } else if(block instanceof LeverBlock lever) {
      lever.pull(state, world, pos, null);
      sound.accept(SoundEvents.LEVER_CLICK, powered ? 1.3f : 1.5f);
    } else if(block instanceof ControlBox.ControlBoxBlock) {
      final BlockEntity te = world.getBlockEntity(pos);
      if(!(te instanceof ControlBox.ControlBoxBlockEntity rlc)) { fail.run(); return; }
      rlc.setEnabled(!rlc.getEnabled());
      sound.accept(SoundEvents.LEVER_CLICK, rlc.getEnabled() ? 1.5f : 1.3f);
    } else {
      fail.run();
    }
  }

  private record RemoteData(BlockPos pos, String name) {}

  private Optional<RemoteData> getRemoteData(ItemStack stack)
  {
    final CompoundTag nbt = Auxiliaries.getItemStackNbt(stack, "remote");
    if((nbt == null) || (!nbt.contains("pos", 99)) || (!nbt.contains("name", 8))) return Optional.empty();
    return Optional.of(new RemoteData(BlockPos.of(nbt.getLong("pos")), nbt.getString("name")));
  }

  private void setRemoteData(ItemStack stack, BlockPos pos, String name)
  {
    final CompoundTag nbt = new CompoundTag();
    nbt.putLong("pos", pos.asLong());
    nbt.putString("name", name);
    Auxiliaries.setItemStackNbt(stack, "remote", nbt);
  }

  private boolean attack(ItemStack stack, BlockPos pos, Player player)
  {
    if(!(stack.getItem() instanceof RemoteItem)) return false;
    if(!(player instanceof ServerPlayer splayer)) return false;
    final BlockState state = splayer.serverLevel().getBlockState(pos);
    if((state.getBlock() instanceof LeverBlock) || (state.getBlock() instanceof ButtonBlock) || (state.getBlock() instanceof ControlBox.ControlBoxBlock) ) {
      final String name = state.getBlock().getDescriptionId();
      setRemoteData(stack, pos, name);
      Overlay.show(splayer, Auxiliaries.localizable("overlay.remote_saved", pos.getX(), pos.getY(), pos.getZ(), Component.translatable(name)), 1500);
    }
    return true;
  }
}
