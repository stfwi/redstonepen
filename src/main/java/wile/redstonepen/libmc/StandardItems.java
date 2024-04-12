/*
 * @file StandardItems.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Common functionality class for decor blocks.
 */
package wile.redstonepen.libmc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;


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

  public static class BaseBlockItem extends BlockItem
  {
    public BaseBlockItem(Block block, Item.Properties properties)
    { super(block, properties); }

    @Override
    @Environment(EnvType.CLIENT)
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag)
    { Auxiliaries.Tooltip.addInformation(stack, ctx, tooltip, flag, true); }

    public InteractionResult useOn(UseOnContext context)
    {
      final InteractionResult ir = onItemUseFirst(context.getItemInHand(), context);
      if(ir != InteractionResult.PASS) return ir;
      return super.useOn(context);
    }

    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context)
    { return InteractionResult.PASS; }
  }

}
