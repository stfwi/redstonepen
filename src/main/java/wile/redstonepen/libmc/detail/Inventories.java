/*
 * @file Inventories.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * General inventory item handling functionality.
 */
package wile.redstonepen.libmc.detail;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

import javax.annotation.Nullable;


public class Inventories
{
  public static boolean areItemStacksIdentical(ItemStack a, ItemStack b)
  { return (a.getItem()==b.getItem()) && ItemStack.areItemStackTagsEqual(a, b); }

  public static boolean areItemStacksDifferent(ItemStack a, ItemStack b)
  { return (a.getItem()!=b.getItem()) || (!ItemStack.areItemStackTagsEqual(a, b)); }

  public static ItemStack extract(IItemHandler inventory, @Nullable ItemStack match, int amount, boolean simulate)
  {
    if((inventory==null) || (amount<=0) || ((match!=null) && (match.isEmpty()))) return ItemStack.EMPTY;
    final int max = inventory.getSlots();
    ItemStack out_stack = new ItemStack(Items.AIR,0);
    for(int i=0; i<max; ++i) {
      final ItemStack stack = inventory.getStackInSlot(i);
      if(stack.isEmpty()) continue;
      if(out_stack.isEmpty()) {
        if((match!=null) && areItemStacksDifferent(stack, match)) continue;
        out_stack = inventory.extractItem(i, amount, simulate);
      } else if(areItemStacksIdentical(stack, out_stack)) {
        ItemStack es = inventory.extractItem(i, (amount-out_stack.getCount()), simulate);
        out_stack.grow(es.getCount());
      }
      if(out_stack.getCount() >= amount) break;
    }
    return out_stack;
  }

  public static ItemStack extract(PlayerEntity player, @Nullable ItemStack match, int amount, boolean simulate)
  { return extract(new InvWrapper(player.inventory), match, amount, simulate); }

  public static ItemStack extract(PlayerEntity player, Item match, int amount, boolean simulate)
  { return extract(player, new ItemStack(match), amount, simulate); }

  public static ItemStack insert(IItemHandler handler, ItemStack stack , boolean simulate)
  { return ItemHandlerHelper.insertItemStacked(handler, stack, simulate); }

  public static ItemStack insert(PlayerEntity player, ItemStack stack , boolean simulate)
  { return insert(new InvWrapper(player.inventory), stack, simulate); }

  public static void give(PlayerEntity entity, ItemStack stack)
  { ItemHandlerHelper.giveItemToPlayer(entity, stack); }

  public static void setItemInPlayerHand(PlayerEntity player, Hand hand, ItemStack stack)
  {
    if(stack.isEmpty()) stack = ItemStack.EMPTY;
    if(hand == Hand.MAIN_HAND) {
      player.inventory.mainInventory.set(player.inventory.currentItem, stack);
    } else {
      player.inventory.offHandInventory.set(0, stack);
    }
  }

}
