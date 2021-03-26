/*
 * @file ExtendedShapelessRecipe.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.libmc.detail;

import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tags.ITag;
import net.minecraft.tags.TagCollectionManager;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistryEntry;
import com.google.common.collect.Lists;
import com.google.gson.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtendedShapelessRecipe extends ShapelessRecipe implements ICraftingRecipe
{
  public interface IRepairableToolItem
  {
    ItemStack onShapelessRecipeRepaired(ItemStack toolStack, int previousDamage, int repairedDamage);
  }

  //--------------------------------------------------------------------------------------------------------------------

  public static final ExtendedShapelessRecipe.Serializer SERIALIZER = ((ExtendedShapelessRecipe.Serializer)(
    (new ExtendedShapelessRecipe.Serializer()).setRegistryName(Auxiliaries.modid(), "crafting_extended_shapeless")
  ));

  //--------------------------------------------------------------------------------------------------------------------

  private final CompoundNBT aspects;
  private final ResourceLocation resultTag;

  public ExtendedShapelessRecipe(ResourceLocation id, String group, ItemStack output, NonNullList<Ingredient> ingredients, CompoundNBT aspects, ResourceLocation resultTag)
  { super(id, group, output, ingredients); this.aspects=aspects; this.resultTag = resultTag; }

  public CompoundNBT getAspects()
  { return aspects.copy(); }

  private int getToolDamage()
  {
    if(aspects.contains("tool_repair")) return (-MathHelper.clamp(aspects.getInt("tool_repair"), 0, 4096));
    if(aspects.contains("tool_damage")) return (MathHelper.clamp(aspects.getInt("tool_damage"), 1, 1024));
    return 0;
  }

  private boolean isRepair()
  { return getToolDamage() < 0; }

  private Tuple<ItemStack, NonNullList<ItemStack>> getRepaired(CraftingInventory inv)
  {
    final String tool_name = aspects.getString("tool");
    final Map<Item, Integer> repair_items = new HashMap<>();
    final NonNullList<ItemStack> remaining = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
    ItemStack tool_item = ItemStack.EMPTY;
    for(int i=0; i<inv.getSizeInventory(); ++i) {
      final ItemStack stack = inv.getStackInSlot(i);
      if(stack.isEmpty()) {
        continue;
      } else if(stack.getItem().getRegistryName().toString().equals(tool_name)) {
        tool_item = stack.copy();
      } else {
        remaining.set(i, stack.copy());
        repair_items.put(stack.getItem(), stack.getCount() + repair_items.getOrDefault(stack.getItem(), 0));
      }
    }
    if(tool_item.isEmpty()) {
      return new Tuple<>(ItemStack.EMPTY, remaining);
    } else if(!tool_item.isDamageable()) {
      Auxiliaries.logWarn("Repairing '"+tool_item.getItem().getRegistryName().toString()+"' can't work, the item is not damageable.");
      return new Tuple<>(ItemStack.EMPTY, remaining);
    } else {
      final int dmg = tool_item.getDamage();
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
      tool_item.setDamage(Math.max(dmg-(single_repair_dur*num_repairs), 0));
      for(int i=0; i<remaining.size(); ++i) {
        ItemStack stack = inv.getStackInSlot(i);
        if(stack.isEmpty()) continue;
        if(stack.getItem().getRegistryName().toString().equals(tool_name)) continue;
        remaining.set(i, stack.hasContainerItem() ? stack.getContainerItem() : stack.copy());
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
        tool_item = ((IRepairableToolItem)(tool_item.getItem())).onShapelessRecipeRepaired(tool_item, dmg, tool_item.getDamage());
      }
      return new Tuple<>(tool_item, remaining);
    }
  }

  @Override
  public boolean isDynamic()
  { return isRepair() || aspects.getBoolean("dynamic"); }

  @Override
  public IRecipeSerializer<?> getSerializer()
  { return ExtendedShapelessRecipe.SERIALIZER; }

  @Override
  public NonNullList<ItemStack> getRemainingItems(CraftingInventory inv)
  {
    if(isRepair()) {
      NonNullList<ItemStack> remaining = getRepaired(inv).getB();
      for(int i=0; i<remaining.size(); ++i) {
        ItemStack rem_stack = remaining.get(i);
        ItemStack inv_stack = inv.getStackInSlot(i);
        if(inv_stack.isEmpty()) continue;
        if(!rem_stack.isEmpty() && !inv.getStackInSlot(i).isItemEqual(rem_stack)) continue;
        remaining.set(i, ItemStack.EMPTY);
        rem_stack.grow(1);
        inv.setInventorySlotContents(i, rem_stack);
      }
      return remaining;
    } else {
      final String tool_name = aspects.getString("tool");
      final int tool_damage = getToolDamage();
      NonNullList<ItemStack> remaining = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
      for(int i=0; i<remaining.size(); ++i) {
        final ItemStack stack = inv.getStackInSlot(i);
        if(stack.getItem().getRegistryName().toString().equals(tool_name)) {
          if(!stack.isDamageable()) {
            remaining.set(i, stack);
          } else { // implicitly !repair
            ItemStack rstack = stack.copy();
            rstack.setDamage(rstack.getDamage()+tool_damage);
            if(rstack.getDamage() < rstack.getMaxDamage()) {
              remaining.set(i, rstack);
            }
          }
        } else if(stack.hasContainerItem()) {
          remaining.set(i, stack.getContainerItem());
        }
      }
      return remaining;
    }
  }

  @Override
  public ItemStack getCraftingResult(CraftingInventory inv)
  {
    if(isRepair()) {
      return getRepaired(inv).getA();
    } else {
      // Initial item crafting
      ItemStack rstack = super.getCraftingResult(inv);
      if(rstack.isEmpty()) return ItemStack.EMPTY;
      if(aspects.getInt("initial_durability") > 0) {
        int dmg = Math.max(0, rstack.getMaxDamage() - aspects.getInt("initial_durability"));
        if(dmg > 0) rstack.setDamage(dmg);
      } else if(aspects.getInt("initial_damage") > 0) {
        int dmg = Math.min(aspects.getInt("initial_damage"), rstack.getMaxDamage());
        if(dmg > 0) rstack.setDamage(dmg);
      }
      return rstack;
    }
  }

  @Override
  public ItemStack getRecipeOutput()
  { return isDynamic() ? ItemStack.EMPTY : super.getRecipeOutput(); }

  //--------------------------------------------------------------------------------------------------------------------

  public static class Serializer extends ForgeRegistryEntry<IRecipeSerializer<?>> implements IRecipeSerializer<ExtendedShapelessRecipe>
  {
    private static int MAX_WIDTH = 3;
    private static int MAX_HEIGHT = 3;

    public Serializer()
    {}

    @Override
    public ExtendedShapelessRecipe read(ResourceLocation recipeId, JsonObject json)
    {
      ResourceLocation resultTag = new ResourceLocation("libmc", "none"); // just no null
      String group = JSONUtils.getString(json, "group", "");
      // Recipe ingredients
      NonNullList<Ingredient> list = NonNullList.create();
      JsonArray ingredients = JSONUtils.getJsonArray(json, "ingredients");
      for(int i = 0; i < ingredients.size(); ++i) {
        Ingredient ingredient = Ingredient.deserialize(ingredients.get(i));
        if (!ingredient.hasNoMatchingItems()) list.add(ingredient);
      }
      if(list.isEmpty()) throw new JsonParseException("No ingredients for "+this.getRegistryName().getPath()+" recipe");
      if(list.size() > MAX_WIDTH * MAX_HEIGHT) throw new JsonParseException("Too many ingredients for crafting_tool_shapeless recipe the max is " + (MAX_WIDTH * MAX_HEIGHT));
      // Extended recipe aspects
      CompoundNBT aspects_nbt = new CompoundNBT();
      if(json.get("aspects")!=null) {
        final JsonObject aspects = JSONUtils.getJsonObject(json, "aspects");
        if(aspects.size() > 0) {
          try {
            aspects_nbt = JsonToNBT.getTagFromJson( (((new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()).toJson(aspects))) );
          } catch(Exception ex) {
            throw new JsonParseException(this.getRegistryName().getPath() + ": Failed to parse the 'aspects' object:" + ex.getMessage());
          }
        }
      }
      // Recipe result
      final JsonObject res = JSONUtils.getJsonObject(json, "result");
      if(res.has("tag")) {
        // Tag based item picking
        ResourceLocation rl = new ResourceLocation(res.get("tag").getAsString());
        ITag<Item> tag = TagCollectionManager.getManager().getItemTags().getIDTagMap().getOrDefault(rl, null);
        if(tag==null) throw new JsonParseException(this.getRegistryName().getPath() + ": Result tag does not exist: #" + rl);
        if(tag.getAllElements().isEmpty()) throw new JsonParseException(this.getRegistryName().getPath() + ": Result tag has no items: #" + rl);
        if(res.has("item")) res.remove("item");
        resultTag = rl;
        List<Item> lst = Lists.newArrayList(tag.getAllElements());
        res.addProperty("item", lst.get(0).getRegistryName().toString());
      }
      ItemStack result_stack = ShapedRecipe.deserializeItem(res);
      return new ExtendedShapelessRecipe(recipeId, group, result_stack, list, aspects_nbt, resultTag);
    }

    @Override
    public ExtendedShapelessRecipe read(ResourceLocation recipeId, PacketBuffer pkt)
    {
      String group = pkt.readString(0x7fff);
      final int size = pkt.readVarInt();
      NonNullList<Ingredient> list = NonNullList.withSize(size, Ingredient.EMPTY);
      for(int i=0; i<list.size(); ++i) list.set(i, Ingredient.read(pkt));
      ItemStack stack = pkt.readItemStack();
      CompoundNBT aspects = pkt.readCompoundTag();
      ResourceLocation resultTag = pkt.readResourceLocation();
      return new ExtendedShapelessRecipe(recipeId, group, stack, list, aspects, resultTag);
    }

    @Override
    public void write(PacketBuffer pkt, ExtendedShapelessRecipe recipe)
    {
      pkt.writeString(recipe.getGroup());
      pkt.writeVarInt(recipe.getIngredients().size());
      for(Ingredient ingredient : recipe.getIngredients()) ingredient.write(pkt);
      pkt.writeItemStack(recipe.getRecipeOutput());
      pkt.writeCompoundTag(recipe.getAspects());
      pkt.writeResourceLocation(recipe.resultTag);
    }
  }
}
