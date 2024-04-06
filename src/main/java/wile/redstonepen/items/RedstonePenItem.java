/*
 * @file BaseItem.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.items;


import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import wile.redstonepen.ModConstants;
import wile.redstonepen.ModContent;
import wile.redstonepen.blocks.CircuitComponents;
import wile.redstonepen.blocks.ControlBox;
import wile.redstonepen.blocks.RedstoneTrack;
import wile.redstonepen.blocks.RedstoneTrack.TrackBlockEntity;
import wile.redstonepen.libmc.*;

import org.jetbrains.annotations.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


@SuppressWarnings("deprecation")
public class RedstonePenItem extends StandardItems.BaseItem
{
  public RedstonePenItem(Item.Properties properties)
  { super(properties); }

  //------------------------------------------------------------------------------------------------------------------

  @Override
  @Environment(EnvType.CLIENT)
  public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag)
  {
    if(getMaxDamage()>0) {
      tooltip.add(Auxiliaries.localizable("item."+ ModConstants.MODID + ".pen.tooltip.numstored", getMaxDamage()-stack.getDamageValue()));
    } else {
      tooltip.add(Auxiliaries.localizable("item."+ ModConstants.MODID + ".pen.tooltip.rsfrominventory"));
    }
    Auxiliaries.Tooltip.addInformation(stack, world, tooltip, flag, true);
  }

  @Override
  public int getEnchantmentValue()
  { return 0; }

  @Override
  public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair)
  { return false; }

  @Override
  public boolean canBeDepleted()
  { return true; }

  @Override
  public boolean isBarVisible(ItemStack stack)
  { return canBeDepleted() && (stack.getDamageValue()>0); }

  @Override
  public int getBarWidth(ItemStack stack)
  {  return (stack.getMaxDamage()<=0) ? (13) : (13-(Mth.clamp(Math.round(13f*stack.getDamageValue()/stack.getMaxDamage()), 0, 13))); }

  @Override
  public int getBarColor(ItemStack stack)
  { return 0x663333; }

  @Override
  public boolean doesSneakBypassUse(ItemStack stack, LevelReader world, BlockPos pos, Player player)
  { return true; }

  @Override
  public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player player)
  {
    if(state.getDestroySpeed(world, pos) > 0) return false;
    if(state.getShape(world, pos).isEmpty()) return false;
    if(state.getBlock() instanceof DiodeBlock) return false;
    if(state.getBlock() instanceof CircuitComponents.DirectedComponentBlock) return false;
    if(state.getBlock() instanceof ControlBox.ControlBoxBlock) return false;
    return true;
  }

  @Override // not really needed, canAttack=false
  public float getDestroySpeed(ItemStack stack, BlockState state)
  { return -1; }

  @Override
  public boolean mineBlock(ItemStack stack, Level world, BlockState state, BlockPos pos, LivingEntity entity)
  {
    if(!(entity instanceof Player player)) return true;
    if(state.getBlock() instanceof DiodeBlock) return false;
    if(state.is(Blocks.REDSTONE_WIRE)) {
      pushRedstone(stack, 1, player);
      world.removeBlock(pos, false);
      return true;
    }
    if(state.is(ModContent.references.TRACK_BLOCK)) {
      HitResult rt = player.pick(10.0, 0f, false);
      if(rt.getType() != HitResult.Type.BLOCK) return false;
      final InteractionHand hand = (player.getItemInHand(InteractionHand.MAIN_HAND).getItem()==this) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
      if(state.getBlock() instanceof RedstoneTrack.RedstoneTrackBlock) {
        ((RedstoneTrack.RedstoneTrackBlock)state.getBlock()).onBlockActivated(state, player.getCommandSenderWorld(), pos, player, hand, ((BlockHitResult)rt), true);
        return true;
      }
    }
    return (state.getDestroySpeed(world, pos) != 0);
  }

  @Override
  public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player)
  {
    final Level world = player.getCommandSenderWorld();
    final BlockState state = world.getBlockState(pos);
    if(state.getBlock() instanceof RepeaterBlock) return false;
    if(state.is(Blocks.REDSTONE_WIRE)) {
      pushRedstone(stack, 1, player);
      world.removeBlock(pos, false);
      return true;
    }
    if(state.is(ModContent.references.TRACK_BLOCK)) {
      HitResult rt = player.pick(10.0, 0f, false);
      if(rt.getType() != HitResult.Type.BLOCK) return false;
      InteractionHand hand = (player.getItemInHand(InteractionHand.MAIN_HAND).getItem()==this) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
      if(state.getBlock() instanceof RedstoneTrack.RedstoneTrackBlock) {
        ((RedstoneTrack.RedstoneTrackBlock)state.getBlock()).onBlockActivated(state, player.getCommandSenderWorld(), pos, player, hand, ((BlockHitResult)rt), true);
        return true;
      }
    }
    return (state.getDestroySpeed(world, pos) != 0);
  }

  @Override
  public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context)
  {
    final Player player = context.getPlayer();
    final InteractionHand hand = context.getHand();
    final BlockPos pos = context.getClickedPos();
    final Direction facing = context.getClickedFace();
    final Level world = context.getLevel();
    final BlockState state = world.getBlockState(pos);
    if(state.is(Blocks.REDSTONE_WIRE)) {
      if(context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
      pushRedstone(stack, 1, player);
      world.removeBlock(pos, false);
      world.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.4f, 2f);
      return InteractionResult.CONSUME;
    }
    if(state.getBlock() instanceof RedstoneTrack.RedstoneTrackBlock) {
      // Add/remove tracks to existing RedstoneTrackBlock
      if(context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
      final BlockHitResult rtr = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
      return ((RedstoneTrack.RedstoneTrackBlock)state.getBlock()).use(state, world, pos, player, hand, rtr);
    }
    if(!RedstoneTrack.RedstoneTrackBlock.canBePlacedOnFace(state, world, pos, facing)) {
      // Cannot place here.
      return InteractionResult.PASS;
    }
    if(context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
    final BlockPos target_pos = pos.relative(facing);
    final BlockState target_state = world.getBlockState(target_pos);
    if(target_state.getBlock() instanceof RedstoneTrack.RedstoneTrackBlock) {
      // Add/remove tracks to existing RedstoneTrackBlock
      final BlockHitResult rtr = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), target_pos, context.isInside());
      return ((RedstoneTrack.RedstoneTrackBlock)target_state.getBlock()).use(target_state, world, target_pos, player, hand, rtr);
    } else {
      final BlockHitResult rtr = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), target_pos, context.isInside());
      final BlockPlaceContext ctx = new BlockPlaceContext(context.getPlayer(), context.getHand(), new ItemStack(Items.REDSTONE), rtr);
      final BlockState rs_state = ModContent.references.TRACK_BLOCK.getStateForPlacement(ctx);
      if(rs_state==null) return InteractionResult.FAIL;
      if(!target_state.getBlock().canBeReplaced(target_state, ctx)) return InteractionResult.FAIL;
      if(!world.setBlock(target_pos, rs_state, 1|2|16)) return InteractionResult.FAIL;
      final BlockState placed_state = world.getBlockState(target_pos);
      if(!(placed_state.getBlock() instanceof RedstoneTrack.RedstoneTrackBlock)) {
        world.removeBlock(target_pos, false);
        return InteractionResult.FAIL;
      } else if(((RedstoneTrack.RedstoneTrackBlock)placed_state.getBlock()).use(placed_state, world, target_pos, player, hand, rtr)==InteractionResult.FAIL) {
        return InteractionResult.FAIL;
      } else {
        ((RedstoneTrack.RedstoneTrackBlock)placed_state.getBlock()).checkSmartPlacement(placed_state, world, target_pos, player, hand, rtr);
        return InteractionResult.CONSUME; // Stack damage already set in onBlockActivated()
      }
    }
  }

  @Override
  public void inventoryTick(ItemStack stack, Level world, Entity entity, int itemSlot, boolean isSelected)
  {
    if((!isSelected) || (!entity.isShiftKeyDown()) || (world.isClientSide()) || ((world.getGameTime() & 0x1) != 0) || (!(entity instanceof ServerPlayer))) return;
    HitResult rt = entity.pick(10.0, 0f, false);
    if(rt.getType() != HitResult.Type.BLOCK) return;
    final BlockHitResult brtr = (BlockHitResult)rt;
    final BlockPos pos = brtr.getBlockPos();
    final BlockState state = world.getBlockState(pos);
    final Block block = state.getBlock();
    final Direction rs_side = brtr.getDirection().getOpposite();
    MutableComponent tc = Component.empty();
    if(block == Blocks.REDSTONE_WIRE) {
      tc = Auxiliaries.localizable("overlay.wire_power", powerFormatted(state.getValue(RedStoneWireBlock.POWER)));
    } else if(block == ModContent.references.TRACK_BLOCK) {
      TrackBlockEntity te = RedstoneTrack.RedstoneTrackBlock.tile(world, pos).orElse(null);
      if(te==null) return;
      tc = Auxiliaries.localizable("overlay.track_power", powerFormatted(te.getSidePower(rs_side)));
      if(Auxiliaries.isDevelopmentMode()) {
        tc.append(Component.literal(String.format(" | flags: %016x, p: ", te.getStateFlags())));
        tc.append(Component.literal(Arrays.stream(Direction.values()).map(side->side.toString().substring(0,1) + te.getRedstonePower(side.getOpposite(), false)).collect(Collectors.joining(","))));
      }
    } else if(state.is(Blocks.REPEATER)) {
      tc = Auxiliaries.localizable("overlay.direct_power", powerFormatted(state.getValue(RepeaterBlock.POWERED) ? 15 : 0));
      tc.append(Auxiliaries.localizable("overlay.repeater_delay", state.getValue(RepeaterBlock.DELAY)));
    } else if(state.is(Blocks.COMPARATOR)) {
      final BlockEntity te = world.getBlockEntity(pos);
      if(te instanceof ComparatorBlockEntity) {
        tc = Auxiliaries.localizable("overlay.direct_power", powerFormatted(((ComparatorBlockEntity)te).getOutputSignal()));
        switch(state.getValue(ComparatorBlock.MODE)) {
          case COMPARE: tc.append(Auxiliaries.localizable("overlay.comparator_compare")); break;
          case SUBTRACT: tc.append(Auxiliaries.localizable("overlay.comparator_subtract")); break;
          default: break;
        }
      }
    } else if(state.isSignalSource()) {
      int p = Math.max(state.getDirectSignal(world, pos, rs_side), state.getSignal(world, pos, rs_side));
      if(p > 0) {
        tc = Auxiliaries.localizable("overlay.direct_power", powerFormatted(p));
      } else {
        Direction max_side = null;
        for(Direction side: Direction.values()) {
          if(side == rs_side) continue;
          int ps = Math.max(state.getDirectSignal(world, pos, side), state.getSignal(world, pos, side));
          if(ps > p) {
            p = ps;
            max_side = side;
            if(p >= 15) break;
          }
        }
        if((p == 0) || (max_side==null)) {
          tc = Auxiliaries.localizable("overlay.direct_power", powerFormatted(p));
        } else {
          tc = Auxiliaries.localizable("overlay.direct_power_at", powerFormatted(p), max_side.getOpposite().toString());
        }
      }
    } else if(RsSignals.canEmitWeakPower(state, world, pos, rs_side)) {
      Direction max_side = Direction.values()[0];
      int p = 0;
      for(Direction d: Direction.values()) {
        int ps = world.getSignal(pos.relative(d), d);
        if(ps>p) { p = ps; max_side=d; if(p>=15){break;} }
      }
      if(p > 0) tc = Auxiliaries.localizable("overlay.indirect_power", powerFormatted(p), max_side.toString()); // @todo: temporary workaround (direction untranslated). Direction may not have a component serialization since codecs are introduced.
    }
    Overlay.show((ServerPlayer)entity, tc, 400);
  }

  //------------------------------------------------------------------------------------------------------------------

  private String powerFormatted(int p)
  { return String.format("%02d", p); }

  //------------------------------------------------------------------------------------------------------------------

  public static void pushRedstone(ItemStack stack, int amount, Player player)
  {
    if(player.isCreative()) return;
    if(amount <= 0) {
      return;
    } else if(isPen(stack)) {
      if(stack.getMaxDamage()<=0) {
        ItemStack remaining = Inventories.insert(player, new ItemStack(Items.REDSTONE, amount), false);
        if(!remaining.isEmpty()) Inventories.give(player, remaining); // also drops, but with sound.
      } else if(stack.getDamageValue() >= amount) {
        stack.setDamageValue(stack.getDamageValue()-amount);
      } else {
        amount -= stack.getDamageValue();
        stack.setDamageValue(0);
        Inventories.give(player, new ItemStack(Items.REDSTONE, amount));
      }
    } else if(stack.getItem() == Items.REDSTONE) {
      if(stack.getCount() <= stack.getMaxStackSize()-amount) {
        stack.grow(amount);
      } else {
        Inventories.give(player, new ItemStack(Items.REDSTONE, amount));
      }
    } else {
      Inventories.give(player, new ItemStack(Items.REDSTONE, amount));
    }
  }

  public static int popRedstone(ItemStack stack, int amount, Player player, InteractionHand hand)
  {
    if(player.isCreative()) return amount;
    if(amount <= 0) {
      return 0;
    } else if(isPen(stack)) {
      if(stack.getMaxDamage() > 0) {
        int dmg = stack.getDamageValue()+amount;
        if(dmg >= stack.getMaxDamage()) {
          amount = stack.getMaxDamage()-stack.getDamageValue();
          player.setItemInHand(hand, ItemStack.EMPTY);
        } else {
          stack.setDamageValue(dmg);
        }
      } else {
        amount = Inventories.extract(player, new ItemStack(Items.REDSTONE), amount, false).getCount();
      }
    } else if(stack.getItem() == Items.REDSTONE) {
      if(stack.getCount() <= amount) {
        amount = stack.getCount();
        player.setItemInHand(hand, ItemStack.EMPTY);
      } else {
        stack.shrink(amount);
      }
    }
    return amount;
  }

  public static boolean hasEnoughRedstone(ItemStack stack, int amount, Player player)
  {
    if(player.isCreative()) return true;
    if(isPen(stack)) {
      if(stack.getMaxDamage() > 0) {
        return stack.getDamageValue() < (stack.getMaxDamage()-amount);
      } else {
        return Inventories.extract(player, new ItemStack(Items.REDSTONE), amount, true).getCount() >= amount;
      }
    } else if(stack.getItem() == Items.REDSTONE) {
      return (stack.getCount() >= amount);
    } else {
      return false;
    }
  }

  public static boolean isFullRedstone(ItemStack stack)
  {
    if(isPen(stack)) return (stack.getDamageValue() <= 0);
    if(stack.getItem() == Items.REDSTONE) return (stack.getCount() >= stack.getMaxStackSize());
    return false;
  }

  public static boolean isPen(ItemStack stack)
  { return (stack.getItem() instanceof RedstonePenItem); }

}
