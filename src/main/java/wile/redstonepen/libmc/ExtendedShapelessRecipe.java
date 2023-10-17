/*
 * @file ExtendedShapelessRecipe.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.libmc;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import java.util.HashMap;
import java.util.Map;

public class ExtendedShapelessRecipe extends ShapelessRecipe implements CraftingRecipe
{
  public interface IRepairableToolItem
  {
    ItemStack onShapelessRecipeRepaired(ItemStack toolStack, int previousDamage, int repairedDamage);
  }

  //--------------------------------------------------------------------------------------------------------------------

  public static final ExtendedShapelessRecipe.Serializer SERIALIZER = new ExtendedShapelessRecipe.Serializer();

  //--------------------------------------------------------------------------------------------------------------------

  private final CompoundTag aspects;
  private final ItemStack resultItem;

  public ExtendedShapelessRecipe(String group, CraftingBookCategory cat, ItemStack output, NonNullList<Ingredient> ingredients, CompoundTag aspects)
  { super(group, cat, output, ingredients); this.aspects=aspects; this.resultItem=output; }

  public CompoundTag getAspects()
  { return aspects.copy(); }

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

  @Override
  public boolean isSpecial()
  { return isRepair() || aspects.getBoolean("dynamic"); }

  @Override
  public RecipeSerializer<?> getSerializer()
  { return ExtendedShapelessRecipe.SERIALIZER; }

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
  public ItemStack assemble(CraftingContainer inv, RegistryAccess ra)
  {
    if(isRepair()) {
      return getRepaired(inv).getA();
    } else {
      // Initial item crafting
      ItemStack rstack = super.assemble(inv, ra);
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

  @Override
  public ItemStack getResultItem(RegistryAccess ra)
  { return isSpecial() ? ItemStack.EMPTY : this.resultItem; }

  //--------------------------------------------------------------------------------------------------------------------

  public static class Serializer implements RecipeSerializer<ExtendedShapelessRecipe>
  {
    private static final Codec<CompoundTag> NBT_CODEC = ExtraCodecs.xor(Codec.STRING, CompoundTag.CODEC).flatXmap(
      either -> either.map(
        s -> {
          try { return DataResult.success(TagParser.parseTag(s)); } catch (CommandSyntaxException e) { return DataResult.error(e::getMessage); }
        }, DataResult::success
      ),
      nbt -> DataResult.success(Either.left(nbt.getAsString()))
    );

    private static final Codec<ExtendedShapelessRecipe> CODEC = RecordCodecBuilder.create((instance) -> (
      instance.group(
        ExtraCodecs.strictOptionalField(Codec.STRING, "group", "").forGetter(ShapelessRecipe::getGroup),
        CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(ShapelessRecipe::category),
        CraftingRecipeCodecs.ITEMSTACK_OBJECT_CODEC.fieldOf("result").forGetter(recipe->recipe.resultItem),
        Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").flatXmap(
          (list) -> {
            final Ingredient[] ingredients = list.stream().filter((ingredient) -> !ingredient.isEmpty()).toArray(Ingredient[]::new);
            if(ingredients.length == 0) {
              return DataResult.error(() -> "No ingredients for shapeless recipe");
            } else {
              return (ingredients.length > 9) ? DataResult.error(() -> "Too many ingredients for shapeless recipe") : DataResult.success(NonNullList.of(Ingredient.EMPTY, ingredients));
            }
          },
          DataResult::success
        ).forGetter(ShapelessRecipe::getIngredients),
        ExtraCodecs.strictOptionalField(NBT_CODEC , "aspects", new CompoundTag()).forGetter(ExtendedShapelessRecipe::getAspects)
      )
      .apply(instance, ExtendedShapelessRecipe::new)
    ));

    public Serializer()
    {}

    public Codec<ExtendedShapelessRecipe> codec()
    { return CODEC; }

    public ExtendedShapelessRecipe fromNetwork(FriendlyByteBuf pkt)
    {
      final String group = pkt.readUtf();
      final CraftingBookCategory cat = pkt.readEnum(CraftingBookCategory.class);
      final int size = pkt.readVarInt();
      final NonNullList<Ingredient> nonNullList = NonNullList.withSize(size, Ingredient.EMPTY);
      nonNullList.replaceAll(ignored->Ingredient.fromNetwork(pkt));
      final ItemStack stack = pkt.readItem();
      final CompoundTag aspects = pkt.readNbt();
      final String resultTag = pkt.readUtf();
      return new ExtendedShapelessRecipe(group, cat, stack, nonNullList, aspects);
    }

    public void toNetwork(FriendlyByteBuf pkt, ExtendedShapelessRecipe recipe)
    {
      pkt.writeUtf(recipe.getGroup());
      pkt.writeEnum(recipe.category());
      pkt.writeVarInt(recipe.getIngredients().size());
      for(Ingredient ingredient: recipe.getIngredients()) ingredient.toNetwork(pkt);
      pkt.writeItem(recipe.getResultItem(null));
      pkt.writeNbt(recipe.getAspects());
    }
  }
}
