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
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraftforge.items.ItemHandlerHelper;

public class Inventories
{
  public static void give(PlayerEntity entity, ItemStack stack)
  { ItemHandlerHelper.giveItemToPlayer(entity, stack); }

  public static void setItemInPlayerHand(PlayerEntity player, Hand hand, ItemStack stack) {
    if(stack.isEmpty()) stack = ItemStack.EMPTY;
    if(hand == Hand.MAIN_HAND) {
      player.inventory.mainInventory.set(player.inventory.currentItem, stack);
    } else {
      player.inventory.offHandInventory.set(0, stack);
    }
  }
}
