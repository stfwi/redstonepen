/*
 * @file ExtendedShapelessRecipe.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.libmc;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITag;

import javax.annotation.Nullable;
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
  private final ResourceLocation resultTag;

  public ExtendedShapelessRecipe(ResourceLocation id, String group, ItemStack output, NonNullList<Ingredient> ingredients, CompoundTag aspects, ResourceLocation resultTag)
  { super(id, group, output, ingredients); this.aspects=aspects; this.resultTag = resultTag; }

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
        remaining.set(i, stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem() : stack.copy());
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
        if(!rem_stack.isEmpty() && !inv.getItem(i).sameItem(rem_stack)) continue;
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
        } else if(stack.hasCraftingRemainingItem()) {
          remaining.set(i, stack.getCraftingRemainingItem());
        }
      }
      return remaining;
    }
  }

  @Override
  public ItemStack assemble(CraftingContainer inv)
  {
    if(isRepair()) {
      return getRepaired(inv).getA();
    } else {
      // Initial item crafting
      ItemStack rstack = super.assemble(inv);
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
  public ItemStack getResultItem()
  { return isSpecial() ? ItemStack.EMPTY : super.getResultItem(); }

  //--------------------------------------------------------------------------------------------------------------------

  public static class Serializer implements RecipeSerializer<ExtendedShapelessRecipe>
  {
    private static final int MAX_WIDTH = 3;
    private static final int MAX_HEIGHT = 3;

    public Serializer()
    {}

    @Override
    @SuppressWarnings("deprecation")
    public ExtendedShapelessRecipe fromJson(ResourceLocation recipeId, JsonObject json)
    {
      ResourceLocation resultTag = new ResourceLocation("libmc", "none"); // just no null
      String group = GsonHelper.getAsString(json, "group", "");
      // Recipe ingredients
      NonNullList<Ingredient> list = NonNullList.create();
      JsonArray ingredients = GsonHelper.getAsJsonArray(json, "ingredients");
      for(int i = 0; i < ingredients.size(); ++i) {
        Ingredient ingredient = Ingredient.fromJson(ingredients.get(i));
        if (!ingredient.isEmpty()) list.add(ingredient);
      }
      if(list.isEmpty()) throw new JsonParseException("No ingredients for " + Registry.RECIPE_SERIALIZER.getKey(this).getPath() + " recipe");
      if(list.size() > MAX_WIDTH * MAX_HEIGHT) throw new JsonParseException("Too many ingredients for crafting_tool_shapeless recipe the max is " + (MAX_WIDTH * MAX_HEIGHT));
      // Extended recipe aspects
      CompoundTag aspects_nbt = new CompoundTag();
      if(json.get("aspects")!=null) {
        final JsonObject aspects = GsonHelper.getAsJsonObject(json, "aspects");
        if(aspects.size() > 0) {
          try {
            aspects_nbt = TagParser.parseTag( (((new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()).toJson(aspects))) );
          } catch(Exception ex) {
            throw new JsonParseException(Registry.RECIPE_SERIALIZER.getKey(this).getPath() + ": Failed to parse the 'aspects' object:" + ex.getMessage());
          }
        }
      }
      // Recipe result
      final JsonObject res = GsonHelper.getAsJsonObject(json, "result");
      if(res.has("tag")) {
        // Tag based item picking
        ResourceLocation rl = new ResourceLocation(res.get("tag").getAsString());
        // yaa that is also gone already: final @Nullable Tag<Item> tag = ItemTags.getAllTags().getTag(rl); // there was something with reload tag availability, Smithies made a fix or so?:::: TagCollectionManager.getInstance().getItems().getAllTags().getOrDefault(rl, null);
        final @Nullable TagKey<Item> key = ForgeRegistries.ITEMS.tags().getTagNames().filter((tag_key->tag_key.location().equals(rl))).findFirst().orElse(null);
        if(key==null) throw new JsonParseException(Registry.RECIPE_SERIALIZER.getKey(this).getPath() + ": Result tag does not exist: #" + rl);
        final ITag<Item> tag = ForgeRegistries.ITEMS.tags().getTag(key);
        final @Nullable Item item = tag.stream().findFirst().orElse(null);
        if(item==null) throw new JsonParseException(Registry.RECIPE_SERIALIZER.getKey(this).getPath() + ": Result tag has no items: #" + rl);
        if(res.has("item")) res.remove("item");
        resultTag = rl;
        res.addProperty("item", Auxiliaries.getResourceLocation(item).toString());
      }
      ItemStack result_stack = ShapedRecipe.itemStackFromJson(res);
      return new ExtendedShapelessRecipe(recipeId, group, result_stack, list, aspects_nbt, resultTag);
    }

    @Override
    public ExtendedShapelessRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf pkt)
    {
      String group = pkt.readUtf(0x7fff);
      final int size = pkt.readVarInt();
      NonNullList<Ingredient> list = NonNullList.withSize(size, Ingredient.EMPTY);
      list.replaceAll(ignored -> Ingredient.fromNetwork(pkt));
      ItemStack stack = pkt.readItem();
      CompoundTag aspects = pkt.readNbt();
      ResourceLocation resultTag = pkt.readResourceLocation();
      return new ExtendedShapelessRecipe(recipeId, group, stack, list, aspects, resultTag);
    }

    @Override
    public void toNetwork(FriendlyByteBuf pkt, ExtendedShapelessRecipe recipe)
    {
      pkt.writeUtf(recipe.getGroup());
      pkt.writeVarInt(recipe.getIngredients().size());
      for(Ingredient ingredient : recipe.getIngredients()) ingredient.toNetwork(pkt);
      pkt.writeItem(recipe.getResultItem());
      pkt.writeNbt(recipe.getAspects());
      pkt.writeResourceLocation(recipe.resultTag);
    }
  }
}
