/*
 * @file BaseItem.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.items;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import wile.redstonepen.ModAnthillInside;
import wile.redstonepen.ModConfig;
import wile.redstonepen.libmc.detail.Auxiliaries;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class RedstonePenItem extends Item
{
  public RedstonePenItem(Item.Properties properties)
  { super(properties.group(ModAnthillInside.ITEMGROUP)); }


  @Override
  public int getItemEnchantability()
  { return 0; }

  @Override
  public boolean getIsRepairable(ItemStack toRepair, ItemStack repair)
  { return false; }

  @Override
  public boolean isDamageable()
  { return false; }

  @Override
  public boolean isEnchantable(ItemStack stack)
  { return false; }


  @Override
  @OnlyIn(Dist.CLIENT)
  public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag)
  { Auxiliaries.Tooltip.addInformation(stack, world, tooltip, flag, true); }

  @Override
  public Collection<ItemGroup> getCreativeTabs()
  { return ModConfig.isOptedOut(this) ? (Collections.emptyList()) : (Collections.singletonList(ModAnthillInside.ITEMGROUP)); }

}
