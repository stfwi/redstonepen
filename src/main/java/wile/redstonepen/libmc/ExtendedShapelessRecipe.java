/*
 * @file ExtendedShapelessRecipe.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.libmc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.util.HashMap;
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
  private final NonNullList<Ingredient> ingredients;
  private final CompoundTag aspects;

  public ExtendedShapelessRecipe(String group, CraftingBookCategory cat, ItemStack output, NonNullList<Ingredient> ingredients, CompoundTag aspects)
  {
    this.group = group;
    this.category = cat;
    this.result = output;
    this.ingredients = ingredients;
    this.aspects=aspects;
  }

  @Override
  public RecipeSerializer<?> getSerializer()
  { return ExtendedShapelessRecipe.SERIALIZER; }

  @Override
  public String getGroup()
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
  public ItemStack getResultItem(HolderLookup.Provider ra)
  { return isSpecial() ? ItemStack.EMPTY : this.result; }

  @Override
  public NonNullList<Ingredient> getIngredients()
  { return this.ingredients; }

  @Override
  public boolean canCraftInDimensions(int i, int j)
  { return i * j >= this.ingredients.size(); }

  @Override
  public NonNullList<ItemStack> getRemainingItems(CraftingContainer inv)
  {
    if(isRepair()) {
      NonNullList<ItemStack> remaining = getRepaired(inv).getB();
      for(int i=0; i<remaining.size(); ++i) {
        ItemStack rem_stack = remaining.get(i);
        ItemStack inv_stack = inv.getItem(i);
        if(inv_stack.isEmpty()) continue;
        if(!rem_stack.isEmpty() && !inv.getItem(i).is(rem_stack.getItem())) continue;
        remaining.set(i, ItemStack.EMPTY);
        rem_stack.grow(1);
        inv.setItem(i, rem_stack);
      }
      return remaining;
    } else {
      final String tool_name = aspects.getString("tool");
      final int tool_damage = getToolDamage();
      NonNullList<ItemStack> remaining = NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);
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
        } else if(stack.getItem().hasCraftingRemainingItem()) {
          remaining.set(i, new ItemStack(stack.getItem().getCraftingRemainingItem(), stack.getCount()));
        }
      }
      return remaining;
    }
  }

  @Override
  public boolean matches(CraftingContainer container, Level world)
  {
    final StackedContents stacked = new StackedContents();
    int i = 0;
    for(int j=0; j<container.getContainerSize(); ++j) {
      final ItemStack ingr = container.getItem(j);
      if(ingr.isEmpty()) continue;
      stacked.accountStack(ingr, 1);
      ++i;
    }
    return (i==this.ingredients.size()) && stacked.canCraft(this, null);
  }

  @Override
  public ItemStack assemble(CraftingContainer inv, HolderLookup.Provider ra)
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

  private Tuple<ItemStack, NonNullList<ItemStack>> getRepaired(CraftingContainer inv)
  {
    final String tool_name = aspects.getString("tool");
    final Map<Item, Integer> repair_items = new HashMap<>();
    final NonNullList<ItemStack> remaining = NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);
    ItemStack tool_item = ItemStack.EMPTY;
    for(int i=0; i<inv.getContainerSize(); ++i) {
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
        remaining.set(i, stack.getItem().hasCraftingRemainingItem() ? new ItemStack(stack.getItem().getCraftingRemainingItem(), stack.getCount()) : stack.copy());
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
    public StreamCodec<RegistryFriendlyByteBuf, ExtendedShapelessRecipe> streamCodec() {
      return STREAM_CODEC;
    }

    @SuppressWarnings("unchecked")
    private static final MapCodec<ExtendedShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(Codec.STRING.optionalFieldOf("group", "")
                .forGetter(r->r.group),
        CraftingBookCategory.CODEC
                .fieldOf("category")
                .orElse(CraftingBookCategory.MISC)
                .forGetter(r->r.category),
        ItemStack.CODEC
                .fieldOf("result")
                .forGetter(r->r.result),
        Ingredient.CODEC_NONEMPTY
                .listOf().fieldOf("ingredients").flatXmap(list -> {
                    final Ingredient[] ingredients = list.stream().filter(ing->!ing.isEmpty()).toArray(Ingredient[]::new);
                    if(ingredients.length == 0) { return DataResult.error(() -> "no ingredients"); }
                    if(ingredients.length > 9) { return DataResult.error(() -> "too many ingredients"); }
                    return DataResult.success(NonNullList.of(Ingredient.EMPTY, ingredients));
                  }, DataResult::success)
                .forGetter(r->r.ingredients),
        CompoundTag.CODEC
                .optionalFieldOf("aspects", new CompoundTag())
                .forGetter(r->r.aspects)
      )
      .apply(instance, ExtendedShapelessRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtendedShapelessRecipe> STREAM_CODEC = StreamCodec.of(
      ExtendedShapelessRecipe.Serializer::toNetwork,
      ExtendedShapelessRecipe.Serializer::fromNetwork
    );

    private static ExtendedShapelessRecipe fromNetwork(RegistryFriendlyByteBuf buf)
    {
      final String group = buf.readUtf();
      final CraftingBookCategory cat = buf.readEnum(CraftingBookCategory.class);
      final int size = buf.readVarInt();
      final NonNullList<Ingredient> ingredients = NonNullList.withSize(size, Ingredient.EMPTY);
      ingredients.replaceAll(ingr->Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
      final ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
      final CompoundTag aspects = buf.readNbt();
      return new ExtendedShapelessRecipe(group, cat, stack, ingredients, aspects);
    }

    private static void toNetwork(RegistryFriendlyByteBuf buf, ExtendedShapelessRecipe recipe)
    {
      buf.writeUtf(recipe.group);
      buf.writeEnum(recipe.category);
      buf.writeVarInt(recipe.ingredients.size());
      for(Ingredient ingredient : recipe.ingredients) { Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient); }
      ItemStack.STREAM_CODEC.encode(buf, recipe.result);
      buf.writeNbt(recipe.getAspects());
    }
  }
}
