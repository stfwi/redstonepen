/*
 * @file Registries.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Common game registry handling.
 */
package wile.redstonepen.libmc;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import wile.redstonepen.ModConstants;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;


public class Registries
{
  private static final Map<String, TagKey<Block>> registered_block_tag_keys = new HashMap<>();
  private static final Map<String, TagKey<Item>> registered_item_tag_keys = new HashMap<>();

  private static final List<Tuple<String, Supplier<? extends Block>>> block_suppliers = new ArrayList<>();
  private static final List<Tuple<String, Supplier<? extends Item>>> item_suppliers = new ArrayList<>();
  private static final List<Tuple<String, Supplier<? extends BlockEntityType<?>>>> block_entity_type_suppliers = new ArrayList<>();
  private static final List<Tuple<String, Supplier<? extends EntityType<?>>>> entity_type_suppliers = new ArrayList<>();
  private static final List<Tuple<String, Supplier<? extends MenuType<?>>>> menu_type_suppliers = new ArrayList<>();
  private static final List<Tuple<String, Supplier<? extends RecipeSerializer<?>>>> recipe_serializers_suppliers = new ArrayList<>();
  private static final List<String> block_item_order = new ArrayList<>();

  private static final Map<String, Block> registered_blocks = new HashMap<>();
  private static final Map<String, Item> registered_items = new HashMap<>();
  private static final Map<String, BlockEntityType<?>> registered_block_entity_types = new HashMap<>();
  private static final Map<String, EntityType<?>> registered_entity_types = new HashMap<>();
  private static final Map<String, MenuType<?>> registered_menu_types = new HashMap<>();
  private static final Map<String, RecipeSerializer<?>> registered_recipe_serializers = new HashMap<>();


  public static void init()
  {
  }

  public static void instantiateAll()
  {
    registered_blocks.clear();
    block_suppliers.forEach((reg)->{
      registered_blocks.put(reg.getA(), reg.getB().get());
      Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, reg.getA()), registered_blocks.get(reg.getA()));
    });
    registered_items.clear();
    item_suppliers.forEach((reg)->{
      registered_items.put(reg.getA(), reg.getB().get());
      Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, reg.getA()), registered_items.get(reg.getA()));
    });
    registered_block_entity_types.clear();
    block_entity_type_suppliers.forEach((reg)->{
      registered_block_entity_types.put(reg.getA(), reg.getB().get());
      Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, reg.getA()), registered_block_entity_types.get(reg.getA()));
    });
    registered_entity_types.clear();
    entity_type_suppliers.forEach((reg)->{
      registered_entity_types.put(reg.getA(), reg.getB().get());
      Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, reg.getA()), registered_entity_types.get(reg.getA()));
    });
    registered_menu_types.clear();
    menu_type_suppliers.forEach((reg)->{
      registered_menu_types.put(reg.getA(), reg.getB().get());
      Registry.register(BuiltInRegistries.MENU, ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, reg.getA()), registered_menu_types.get(reg.getA()));
    });
    registered_recipe_serializers.clear();
    recipe_serializers_suppliers.forEach((reg)->{
      registered_recipe_serializers.put(reg.getA(), reg.getB().get());
      Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, reg.getA()), registered_recipe_serializers.get(reg.getA()));
    });
  }

  // -------------------------------------------------------------------------------------------------------------

  public static Block getBlock(String block_name)
  { return registered_blocks.get(block_name); }

  public static Item getItem(String name)
  { return registered_items.get(name); }

  public static EntityType<?> getEntityType(String name)
  { return registered_entity_types.get(name); }

  public static BlockEntityType<?> getBlockEntityType(String block_name)
  { return registered_block_entity_types.get(block_name); }

  public static MenuType<?> getMenuType(String name)
  { return registered_menu_types.get(name); }

  public static RecipeSerializer<?> getRecipeSerializer(String name)
  { return registered_recipe_serializers.get(name); }

  public static BlockEntityType<?> getBlockEntityTypeOfBlock(String block_name)
  { return getBlockEntityType("tet_"+block_name); }

  public static BlockEntityType<?> getBlockEntityTypeOfBlock(Block block)
  { return getBlockEntityTypeOfBlock(BuiltInRegistries.BLOCK.getKey(block).getPath()); }

  public static MenuType<?> getMenuTypeOfBlock(String name)
  { return getMenuType("ct_"+name); }

  public static MenuType<?> getMenuTypeOfBlock(Block block)
  { return getMenuTypeOfBlock(BuiltInRegistries.BLOCK.getKey(block).getPath()); }

  public static TagKey<Block> getBlockTagKey(String name)
  { return registered_block_tag_keys.get(name); }

  public static TagKey<Item> getItemTagKey(String name)
  { return registered_item_tag_keys.get(name); }

  // -------------------------------------------------------------------------------------------------------------

  public static List<Block> getRegisteredBlocks()
  { return registered_blocks.values().stream().toList(); }

  public static List<Item> getRegisteredItems()
  { return registered_items.values().stream().toList(); }

  public static List<BlockEntityType<?>> getRegisteredBlockEntityTypes()
  { return registered_block_entity_types.values().stream().toList(); }

  public static List<EntityType<?>> getRegisteredEntityTypes()
  { return registered_entity_types.values().stream().toList(); }

  // -------------------------------------------------------------------------------------------------------------

  public static <T extends Item> void addItem(String registry_name, Supplier<T> supplier)
  { item_suppliers.add(new Tuple<>(registry_name, supplier)); }

  public static <T extends Block> void addBlock(String registry_name, Supplier<T> block_supplier)
  {
    block_suppliers.add(new Tuple<>(registry_name, block_supplier));
    item_suppliers.add(new Tuple<>(registry_name, ()->new BlockItem(registered_blocks.get(registry_name), new Item.Properties())));
  }

  public static <TB extends Block, TI extends Item> void addBlock(String registry_name, Supplier<TB> block_supplier, Supplier<TI> item_supplier)
  {
    block_suppliers.add(new Tuple<>(registry_name, block_supplier));
    item_suppliers.add(new Tuple<>(registry_name, item_supplier));
  }

  public static <T extends BlockEntity> void addBlockEntityType(String registry_name, BlockEntityType.BlockEntitySupplier<T> ctor, String... block_names)
  {
    block_entity_type_suppliers.add(new Tuple<>(registry_name, ()->{
      final Block[] blocks = Arrays.stream(block_names).map(s -> {
        Block b = registered_blocks.get(s);
        if (b == null) Auxiliaries.logError("registered_blocks does not encompass '" + s + "'");
        return b;
      }).filter(Objects::nonNull).toList().toArray(new Block[]{});
      return BlockEntityType.Builder.of(ctor, blocks).build(null);
    }));
  }

  public static <T extends EntityType<?>> void addEntityType(String registry_name, Supplier<EntityType<?>> supplier)
  { entity_type_suppliers.add(new Tuple<>(registry_name, supplier)); }

  public static <T extends MenuType<?>> void addMenuType(String registry_name, MenuType.MenuSupplier<?> supplier)
  { menu_type_suppliers.add(new Tuple<>(registry_name, ()->new MenuType<>(supplier, FeatureFlagSet.of()))); }

  public static void addRecipeSerializer(String registry_name, Supplier<? extends RecipeSerializer<?>> serializer_supplier)
  { recipe_serializers_suppliers.add(new Tuple<>(registry_name, serializer_supplier)); }

  // -------------------------------------------------------------------------------------------------------------

  public static <TB extends Block, TI extends Item> void addBlock(String registry_name, Supplier<TB> block_supplier, BiFunction<Block, Item.Properties, Item> item_builder)
  { addBlock(registry_name, block_supplier, ()->item_builder.apply(registered_blocks.get(registry_name), new Item.Properties())); }

  public static void addBlock(String registry_name, Supplier<? extends Block> block_supplier, BlockEntityType.BlockEntitySupplier<?> block_entity_ctor)
  {
    addBlock(registry_name, block_supplier);
    addBlockEntityType("tet_"+registry_name, block_entity_ctor, registry_name);
  }

  public static void addBlock(String registry_name, Supplier<? extends Block> block_supplier, BiFunction<Block, Item.Properties, Item> item_builder, BlockEntityType.BlockEntitySupplier<?> block_entity_ctor, MenuType.MenuSupplier<?> menu_type_supplier)
  {
    addBlock(registry_name, block_supplier, item_builder);
    addBlockEntityType("tet_"+registry_name, block_entity_ctor, registry_name);
    addMenuType("ct_"+registry_name, menu_type_supplier);
  }

  public static void addBlock(String registry_name, Supplier<? extends Block> block_supplier, BlockEntityType.BlockEntitySupplier<?> block_entity_ctor, MenuType.MenuSupplier<?> menu_type_supplier)
  {
    addBlock(registry_name, block_supplier, block_entity_ctor);
    addMenuType("ct_"+registry_name, menu_type_supplier);
  }

}
