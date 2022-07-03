/*
 * @file StandardItems.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Common functionality class for decor blocks.
 */
package wile.redstonepen.libmc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelReader;


public class StandardItems
{
  public interface IStandardItem //extends IForgeItem
  {
  }

  public static class BaseItem extends Item implements IStandardItem
  {
    public BaseItem(Properties properties)
    { super(properties); }

    public boolean doesSneakBypassUse(ItemStack stack, LevelReader world, BlockPos pos, Player player)
    { return false; }

    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player)
    { return false; }

    public InteractionResult useOn(UseOnContext context)
    { return onItemUseFirst(context.getItemInHand(), context); }

    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context)
    { return InteractionResult.PASS; }
  }

}
