/**
 * @file ExtendedShapelessRecipe.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.libmc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtendedShapelessRecipe implements CraftingRecipe
{
  public interface IRepairableToolItem
  {
    ItemStack onShapelessRecipeRepaired(ItemStack toolStack, int previousDamage, int repairedDamage);
  }

  //--------------------------------------------------------------------------------------------------------------------
  private final String group;
  private final CraftingBookCategory category;
  private final ItemStack result;
  private final List<Ingredient> ingredients;
  private final CompoundTag aspects;

  @Nullable private PlacementInfo placement = null;

  public ExtendedShapelessRecipe(String group, CraftingBookCategory cat, ItemStack output, List<Ingredient> ingredients, CompoundTag aspects)
  {
    this.group = group;
    this.category = cat;
    this.result = output;
    this.ingredients = ingredients;
    this.aspects=aspects;
  }

  @Override
  public RecipeSerializer<ExtendedShapelessRecipe> getSerializer()
  { return ExtendedShapelessRecipe.SERIALIZER; }

  @Override
  public String group()
  { return this.group; }

  @Override
  public CraftingBookCategory category()
  { return this.category; }

  public CompoundTag getAspects()
  { return aspects.copy(); }

  @Override
  public boolean isSpecial()
  { return isRepair() || aspects.getBoolean("dynamic"); }

  @Override
  public PlacementInfo placementInfo() {
    if(placement == null) placement = PlacementInfo.create(this.ingredients);
    return placement;
  }

  @Override
  public NonNullList<ItemStack> getRemainingItems(CraftingInput inv)
  {
    if(isRepair()) {
      NonNullList<ItemStack> remaining = getRepaired(inv).getB();
      for(int i=0; i<remaining.size(); ++i) {
        ItemStack rem_stack = remaining.get(i);
        ItemStack inv_stack = inv.getItem(i);
        if(inv_stack.isEmpty()) continue;
        if(!rem_stack.isEmpty() && !inv.getItem(i).is(rem_stack.getItem())) continue;
        remaining.set(i, ItemStack.EMPTY);
        if(!rem_stack.isEmpty()) rem_stack.grow(1);
        inv_stack.setCount(rem_stack.getCount());
      }
      return remaining;
    } else {
      final String tool_name = aspects.getString("tool");
      final int tool_damage = getToolDamage();
      NonNullList<ItemStack> remaining = NonNullList.withSize(inv.size(), ItemStack.EMPTY);
      for(int i=0; i<remaining.size(); ++i) {
        final ItemStack stack = inv.getItem(i);
        if(Auxiliaries.getResourceLocation(stack.getItem()).toString().equals(tool_name)) {
          if(!stack.isDamageableItem()) {
            remaining.set(i, stack);
          } else { // implicitly !repair
            ItemStack rstack = stack.copy();
            rstack.setDamageValue(rstack.getDamageValue()+tool_damage);
            if(rstack.getDamageValue() < rstack.getMaxDamage()) {
              remaining.set(i, rstack);
            }
          }
        } else if(stack.getItem().getCraftingRemainder().isEmpty()) {
          remaining.set(i, new ItemStack(stack.getItem().getCraftingRemainder().getItem(), stack.getCount()));
        }
      }
      return remaining;
    }
  }

  @Override
  public boolean matches(CraftingInput input, Level world)
  {
    final StackedItemContents stacked = new StackedItemContents();
    int i = 0;
    for(int j=0; j<input.size(); ++j) {
      final ItemStack ingr = input.getItem(j);
      if(ingr.isEmpty()) continue;
      stacked.accountStack(ingr, 1);
      ++i;
    }
    return (i==this.ingredients.size()) && stacked.canCraft(this, null);
  }

  @Override
  public ItemStack assemble(CraftingInput inv, HolderLookup.Provider ra)
  {
    if(isRepair()) {
      return getRepaired(inv).getA();
    } else {
      // Initial item crafting
      ItemStack rstack = result.copy();
      if(rstack.isEmpty()) return ItemStack.EMPTY;
      if(aspects.getInt("initial_durability") > 0) {
        int dmg = Math.max(0, rstack.getMaxDamage() - aspects.getInt("initial_durability"));
        if(dmg > 0) rstack.setDamageValue(dmg);
      } else if(aspects.getInt("initial_damage") > 0) {
        int dmg = Math.min(aspects.getInt("initial_damage"), rstack.getMaxDamage());
        if(dmg > 0) rstack.setDamageValue(dmg);
      }
      return rstack;
    }
  }

  //--------------------------------------------------------------------------------------------------------------------

  private int getToolDamage()
  {
    if(aspects.contains("tool_repair")) return (-Mth.clamp(aspects.getInt("tool_repair"), 0, 4096));
    if(aspects.contains("tool_damage")) return (Mth.clamp(aspects.getInt("tool_damage"), 1, 1024));
    return 0;
  }

  private boolean isRepair()
  { return getToolDamage() < 0; }

  private Tuple<ItemStack, NonNullList<ItemStack>> getRepaired(CraftingInput inv)
  {
    final String tool_name = aspects.getString("tool");
    final Map<Item, Integer> repair_items = new HashMap<>();
    final NonNullList<ItemStack> remaining = NonNullList.withSize(inv.size(), ItemStack.EMPTY);
    ItemStack tool_item = ItemStack.EMPTY;
    for(int i=0; i<inv.size(); ++i) {
      final ItemStack stack = inv.getItem(i);
      if(stack.isEmpty()) {
        continue;
      } else if(Auxiliaries.getResourceLocation(stack.getItem()).toString().equals(tool_name)) {
        tool_item = stack.copy();
      } else {
        remaining.set(i, stack.copy());
        repair_items.put(stack.getItem(), stack.getCount() + repair_items.getOrDefault(stack.getItem(), 0));
      }
    }
    if(tool_item.isEmpty()) {
      return new Tuple<>(ItemStack.EMPTY, remaining);
    } else if(!tool_item.isDamageableItem()) {
      Auxiliaries.logWarn("Repairing '" +  Auxiliaries.getResourceLocation(tool_item.getItem()) +"' can't work, the item is not damageable.");
      return new Tuple<>(ItemStack.EMPTY, remaining);
    } else {
      final int dmg = tool_item.getDamageValue();
      if((dmg <= 0) && (!aspects.getBoolean("over_repair"))) return new Tuple<>(ItemStack.EMPTY, remaining);
      final int min_repair_item_count = repair_items.values().stream().mapToInt(Integer::intValue).min().orElse(0);
      if(min_repair_item_count <= 0) return new Tuple<>(ItemStack.EMPTY, remaining);
      final int single_repair_dur = aspects.getBoolean("relative_repair_damage")
        ? Math.max(1, -getToolDamage() * tool_item.getMaxDamage() / 100)
        : Math.max(1, -getToolDamage());
      int num_repairs = dmg/single_repair_dur;
      if(num_repairs*single_repair_dur < dmg) ++num_repairs;
      num_repairs = Math.min(num_repairs, min_repair_item_count);
      for(Item ki: repair_items.keySet()) repair_items.put(ki, num_repairs);
      tool_item.setDamageValue(Math.max(dmg-(single_repair_dur*num_repairs), 0));
      for(int i=0; i<remaining.size(); ++i) {
        ItemStack stack = inv.getItem(i);
        if(stack.isEmpty()) continue;
        if(Auxiliaries.getResourceLocation(stack.getItem()).toString().equals(tool_name)) continue;
        remaining.set(i, (!stack.getItem().getCraftingRemainder().isEmpty()) ? new ItemStack(stack.getItem().getCraftingRemainder().getItem(), stack.getCount()) : stack.copy()); // remaining.set(i, stack.getItem().hasCraftingRemainingItem() ? new ItemStack(stack.getItem().getCraftingRemainingItem(), stack.getCount()) : stack.copy());
      }
      for(int i=0; i<remaining.size(); ++i) {
        final ItemStack stack = remaining.get(i);
        final Item item = stack.getItem();
        if(!repair_items.containsKey(item)) continue;
        int n = repair_items.get(item);
        if(stack.getCount() >= n) {
          stack.shrink(n);
          repair_items.remove(item);
        } else {
          repair_items.put(item, n-stack.getCount());
          remaining.set(i, ItemStack.EMPTY);
        }
      }
      if((tool_item.getItem() instanceof IRepairableToolItem)) {
        tool_item = ((IRepairableToolItem)(tool_item.getItem())).onShapelessRecipeRepaired(tool_item, dmg, tool_item.getDamageValue());
      }
      return new Tuple<>(tool_item, remaining);
    }
  }

  //--------------------------------------------------------------------------------------------------------------------

  public static final ExtendedShapelessRecipe.Serializer SERIALIZER = new ExtendedShapelessRecipe.Serializer();

  public static class Serializer implements RecipeSerializer<ExtendedShapelessRecipe>
  {
    @Override
    public MapCodec<ExtendedShapelessRecipe> codec() {
      return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, ExtendedShapelessRecipe> streamCodec()
    { return STREAM_CODEC; }

    public static final MapCodec<ExtendedShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(smc -> smc.group(
        Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.group),
        CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(r -> r.category),
        ItemStack.CODEC.fieldOf("result").forGetter(r -> r.result),
        Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(r -> r.ingredients),
        CompoundTag.CODEC.optionalFieldOf("aspects", new CompoundTag()).forGetter(r->r.aspects)
      )
      .apply(smc, ExtendedShapelessRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtendedShapelessRecipe> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.STRING_UTF8,            r -> r.group,
      CraftingBookCategory.STREAM_CODEC,    r -> r.category,
      ItemStack.STREAM_CODEC,               r -> r.result,
      Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),  r -> r.ingredients,
      ByteBufCodecs.COMPOUND_TAG,           r -> r.aspects,
      ExtendedShapelessRecipe::new
    );
  }
}
