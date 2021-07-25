/*
 * @file BaseItem.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.items;

import net.minecraft.block.*;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.*;
import net.minecraft.tileentity.ComparatorTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import wile.redstonepen.ModContent;
import wile.redstonepen.ModRedstonePen;
import wile.redstonepen.blocks.RedstoneTrack;
import wile.redstonepen.blocks.RedstoneTrack.TrackTileEntity;
import wile.redstonepen.libmc.detail.Auxiliaries;
import wile.redstonepen.libmc.detail.Inventories;
import wile.redstonepen.libmc.detail.Overlay;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


public class RedstonePenItem extends Item
{
  public RedstonePenItem(Item.Properties properties)
  { super(properties.tab(ModRedstonePen.ITEMGROUP).setNoRepair()); }

  //------------------------------------------------------------------------------------------------------------------

  @Override
  @OnlyIn(Dist.CLIENT)
  public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag)
  {
    if(getMaxDamage(stack)>0) {
      tooltip.add(Auxiliaries.localizable("item."+ ModRedstonePen.MODID + ".pen.tooltip.numstored", getMaxDamage(stack)-getDamage(stack)));
    } else {
      tooltip.add(Auxiliaries.localizable("item."+ ModRedstonePen.MODID + ".pen.tooltip.rsfrominventory"));
    }
    Auxiliaries.Tooltip.addInformation(stack, world, tooltip, flag, true);
  }

  @Override
  public Collection<ItemGroup> getCreativeTabs()
  { return (Collections.singletonList(ModRedstonePen.ITEMGROUP)); }

  @Override
  public int getEnchantmentValue()
  { return 0; }

  @Override
  public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair)
  { return false; }

  @Override
  @SuppressWarnings("deprecation")
  public boolean canBeDepleted()
  { return getMaxDamage()>0; }

  @Override
  public boolean isBookEnchantable(ItemStack stack, ItemStack book)
  { return false; }

  @Override
  public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment)
  { return false; }

  @Override
  public boolean showDurabilityBar(ItemStack stack)
  { return canBeDepleted() && (stack.getDamageValue()>0); }

  @Override
  public double getDurabilityForDisplay(ItemStack stack)
  { return (stack.getMaxDamage()<=0) ? (1.0) : MathHelper.clamp((double)stack.getDamageValue()/(double)stack.getMaxDamage(), 0.0, 1.0); }

  @Override
  public int getRGBDurabilityForDisplay(ItemStack stack)
  { return 0x663333; }

  @Override
  public boolean doesSneakBypassUse(ItemStack stack, IWorldReader world, BlockPos pos, PlayerEntity player)
  { return true; }

  @Override
  @SuppressWarnings("deprecation")
  public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, PlayerEntity player)
  {
    final World world = player.getCommandSenderWorld();
    final BlockState state = world.getBlockState(pos);
    if(state.getBlock() instanceof RedstoneDiodeBlock) return false;
    if(state.is(Blocks.REDSTONE_WIRE)) {
      pushRedstone(stack, 1, player);
      world.removeBlock(pos, false);
      return true;
    }
    if(state.is(ModContent.TRACK_BLOCK)) {
      RayTraceResult rt = player.pick(10.0, 0f, false);
      final BlockRayTraceResult brtr = (BlockRayTraceResult)rt;
      if(rt.getType() != RayTraceResult.Type.BLOCK) return false;
      Hand hand = (player.getItemInHand(Hand.MAIN_HAND).getItem()==this) ? Hand.MAIN_HAND : Hand.OFF_HAND;
      if(state.getBlock() instanceof RedstoneTrack.RedstoneTrackBlock) {
        ((RedstoneTrack.RedstoneTrackBlock)state.getBlock()).onBlockActivated(state, player.getCommandSenderWorld(), pos, player, hand, ((BlockRayTraceResult)rt), true);
        return true;
      }
    }
    return (state.getDestroySpeed(world, pos) != 0);
  }

  @Override
  @SuppressWarnings("deprecation")
  public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
  {
    final PlayerEntity player = context.getPlayer();
    final Hand hand = context.getHand();
    final BlockPos pos = context.getClickedPos();
    final Direction facing = context.getClickedFace();
    final World world = context.getLevel();
    final BlockState state = world.getBlockState(pos);
    if(state.is(Blocks.REDSTONE_WIRE)) {
      if(context.getLevel().isClientSide()) return ActionResultType.SUCCESS;
      pushRedstone(stack, 1, player);
      world.removeBlock(pos, false);
      world.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 0.4f, 2f);
      return ActionResultType.CONSUME;
    }
    if(state.getBlock() instanceof RedstoneTrack.RedstoneTrackBlock) {
      // Add/remove tracks to existing RedstoneTrackBlock
      if(context.getLevel().isClientSide()) return ActionResultType.SUCCESS;
      if(player.getOffhandItem().getItem() == Items.DEBUG_STICK) {
        TileEntity te = world.getBlockEntity(pos);
        if(te instanceof RedstoneTrack.TrackTileEntity) ((RedstoneTrack.TrackTileEntity)te).toggle_trace(player);
        return ActionResultType.CONSUME;
      }
      final BlockRayTraceResult rtr = new BlockRayTraceResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
      return ((RedstoneTrack.RedstoneTrackBlock)state.getBlock()).use(state, world, pos, player, hand, rtr);
    }
    if(!RedstoneTrack.RedstoneTrackBlock.canBePlacedOnFace(state, world, pos, facing)) {
      // Cannot place here.
      return ActionResultType.PASS;
    }
    if(context.getLevel().isClientSide()) return ActionResultType.SUCCESS;
    final BlockPos target_pos = pos.relative(facing);
    final BlockState target_state = world.getBlockState(target_pos);
    if(target_state.getBlock() instanceof RedstoneTrack.RedstoneTrackBlock) {
      // Add/remove tracks to existing RedstoneTrackBlock
      final BlockRayTraceResult rtr = new BlockRayTraceResult(context.getClickLocation(), context.getClickedFace(), target_pos, context.isInside());
      return ((RedstoneTrack.RedstoneTrackBlock)target_state.getBlock()).use(target_state, world, target_pos, player, hand, rtr);
    } else {
      final BlockRayTraceResult rtr = new BlockRayTraceResult(context.getClickLocation(), context.getClickedFace(), target_pos, context.isInside());
      final BlockItemUseContext ctx = new BlockItemUseContext(context.getPlayer(), context.getHand(), new ItemStack(Items.REDSTONE), rtr);
      final BlockState rs_state = ModContent.TRACK_BLOCK.getStateForPlacement(ctx);
      if(rs_state==null) return ActionResultType.FAIL;
      if(!state.getBlock().canBeReplaced(target_state, ctx)) return ActionResultType.FAIL;
      if(!world.setBlock(target_pos, rs_state, 1|2)) return ActionResultType.FAIL;
      final BlockState placed_state = world.getBlockState(target_pos);
      if(!(placed_state.getBlock() instanceof RedstoneTrack.RedstoneTrackBlock)) {
        world.removeBlock(target_pos, false);
        return ActionResultType.FAIL;
      } else if(((RedstoneTrack.RedstoneTrackBlock)placed_state.getBlock()).use(placed_state, world, target_pos, player, hand, rtr)==ActionResultType.FAIL) {
        return ActionResultType.FAIL;
      } else {
        ((RedstoneTrack.RedstoneTrackBlock)placed_state.getBlock()).checkSmartPlacement(placed_state, world, target_pos, player, hand, rtr);
        return ActionResultType.CONSUME; // Stack damage already set in onBlockActivated()
      }
    }
  }

  @Override
  public void inventoryTick(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected)
  {
    if((!isSelected) || (!entity.isShiftKeyDown()) || (world.isClientSide()) || ((world.getGameTime() & 0x1) != 0) || (!(entity instanceof ServerPlayerEntity))) return;
    RayTraceResult rt = entity.pick(10.0, 0f, false);
    if(rt.getType() != RayTraceResult.Type.BLOCK) return;
    final BlockRayTraceResult brtr = (BlockRayTraceResult)rt;
    final BlockPos pos = brtr.getBlockPos();
    final BlockState state = world.getBlockState(pos);
    final Block block = state.getBlock();
    final Direction rs_side = brtr.getDirection().getOpposite();
    TranslationTextComponent tc = null;
    if(block == Blocks.REDSTONE_WIRE) {
      tc = Auxiliaries.localizable("overlay.wire_power", powerFormatted(state.getValue(RedstoneWireBlock.POWER)));
    } else if(block == ModContent.TRACK_BLOCK) {
      TrackTileEntity te = RedstoneTrack.RedstoneTrackBlock.tile(world, pos).orElse(null);
      if(te==null) return;
      tc = Auxiliaries.localizable("overlay.track_power", powerFormatted(te.getSidePower(rs_side)));
      if(Auxiliaries.isDevelopmentMode()) {
        tc.append(new StringTextComponent(String.format(" | %016x | ", te.getStateFlags())));
        tc.append(new StringTextComponent(Arrays.stream(Direction.values()).map(side->side.toString().substring(0,1) + te.getRedstonePower(side.getOpposite(), false)).collect(Collectors.joining(","))));
      }
    } else if(state.is(Blocks.REPEATER)) {
      tc = Auxiliaries.localizable("overlay.direct_power", powerFormatted(state.getValue(RepeaterBlock.POWERED) ? 15 : 0));
      tc.append(Auxiliaries.localizable("overlay.repeater_delay", state.getValue(RepeaterBlock.DELAY)));
    } else if(state.is(Blocks.COMPARATOR)) {
      final TileEntity te = world.getBlockEntity(pos);
      if(te instanceof ComparatorTileEntity) {
        tc = Auxiliaries.localizable("overlay.direct_power", powerFormatted(((ComparatorTileEntity)te).getOutputSignal()));
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
          tc = Auxiliaries.localizable("overlay.direct_power_at", powerFormatted(p), max_side.getOpposite());
        }
      }
    } else if(state.shouldCheckWeakPower(world, pos, rs_side)) {
      Direction max_side = Direction.values()[0];
      int p = 0;
      for(Direction d: Direction.values()) {
        int ps = world.getSignal(pos.relative(d), d);
        if(ps>p) { p = ps; max_side=d; if(p>=15){break;} }
      }
      if(p > 0) tc = Auxiliaries.localizable("overlay.indirect_power", powerFormatted(p), max_side);
    }
    if(tc!=null) Overlay.show((ServerPlayerEntity)entity, tc, 400);
  }

  //------------------------------------------------------------------------------------------------------------------

  private String powerFormatted(int p)
  { return String.format("%02d", p); }

  //------------------------------------------------------------------------------------------------------------------

  public static final void pushRedstone(ItemStack stack, int amount, PlayerEntity player)
  {
    if(amount <= 0) {
      return;
    } else if(isPen(stack)) {
      if(!stack.isDamageableItem()) {
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

  public static final int popRedstone(ItemStack stack, int amount, PlayerEntity player, Hand hand)
  {
    if(amount <= 0) {
      return 0;
    } else if(isPen(stack)) {
      if(stack.isDamageableItem()) {
        int dmg = stack.getDamageValue()+amount;
        if(dmg >= stack.getMaxDamage()) {
          amount = stack.getMaxDamage()-stack.getDamageValue();
          player.setItemInHand(hand, ItemStack.EMPTY);
        } else {
          stack.setDamageValue(dmg);
        }
      } else {
        amount = Inventories.extract(player, Items.REDSTONE, amount, false).getCount();
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

  public static final boolean hasEnoughRedstone(ItemStack stack, int amount, PlayerEntity player)
  {
    if(isPen(stack)) {
      if(stack.isDamageableItem()) {
        return stack.getDamageValue() < (stack.getMaxDamage()-amount);
      } else {
        return Inventories.extract(player, Items.REDSTONE, amount, true).getCount() >= amount;
      }
    } else if(stack.getItem() == Items.REDSTONE) {
      return (stack.getCount() >= amount);
    } else {
      return false;
    }
  }

  public static final boolean isFullRedstone(ItemStack stack)
  {
    if(isPen(stack)) return (stack.getDamageValue() <= 0);
    if(stack.getItem() == Items.REDSTONE) return (stack.getCount() >= stack.getMaxStackSize());
    return false;
  }

  public static final boolean isPen(ItemStack stack)
  { return (stack.getItem() instanceof RedstonePenItem); }

}
