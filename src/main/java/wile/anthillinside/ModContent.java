/*
 * @file ModContent.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.entity.EntityType;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Rarity;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialColor;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.Logger;
import wile.redstonepen.blocks.*;
import wile.redstonepen.items.*;
import wile.redstonepen.libmc.blocks.StandardBlocks;
import wile.redstonepen.libmc.blocks.StandardBlocks.IStandardBlock;
import wile.redstonepen.libmc.detail.Auxiliaries;

import javax.annotation.Nonnull;
import java.util.*;


public class ModContent
{
  private static final Logger LOGGER = ModAnthillInside.LOGGER;
  private static final String MODID = ModAnthillInside.MODID;

  // -----------------------------------------------------------------------------------------------------------------
  // -- Blocks
  // -----------------------------------------------------------------------------------------------------------------

  public static final RedAntHive.RedAntHiveBlock HIVE_BLOCK = (RedAntHive.RedAntHiveBlock)(new RedAntHive.RedAntHiveBlock(
    StandardBlocks.CFG_CUTOUT|StandardBlocks.CFG_WATERLOGGABLE|StandardBlocks.CFG_LOOK_PLACEMENT,
    Block.Properties.create(Material.ROCK, MaterialColor.STONE).hardnessAndResistance(2f, 6f).sound(SoundType.STONE),
    new AxisAlignedBB[]{
      Auxiliaries.getPixeledAABB(1,1,0,15,15, 1),
      Auxiliaries.getPixeledAABB(0,0,1,16,16,16),
    }
  )).setRegistryName(new ResourceLocation(MODID, "hive"));

  public static final RedAntTrail.RedAntTrailBlock TRAIL_BLOCK = (RedAntTrail.RedAntTrailBlock)(new RedAntTrail.RedAntTrailBlock(
    StandardBlocks.CFG_TRANSLUCENT|StandardBlocks.CFG_HORIZIONTAL|StandardBlocks.CFG_LOOK_PLACEMENT,
    Block.Properties.create(Material.MISCELLANEOUS, MaterialColor.BROWN).hardnessAndResistance(0.1f, 3f).sound(SoundType.CROP)
      .doesNotBlockMovement().notSolid().setAllowsSpawn((s,w,p,e)->false).jumpFactor(1.2f).tickRandomly()
  )).setRegistryName(new ResourceLocation(MODID, "trail"));

  private static final Block modBlocks[] = {
    HIVE_BLOCK,
    TRAIL_BLOCK
  };

  //--------------------------------------------------------------------------------------------------------------------
  // Items
  //--------------------------------------------------------------------------------------------------------------------

  private static Item.Properties default_item_properties()
  { return (new Item.Properties()).group(ModAnthillInside.ITEMGROUP); }

  public static final RedSugarItem RED_SUGAR_ITEM = (RedSugarItem)((new RedSugarItem(
    default_item_properties().rarity(Rarity.UNCOMMON)
  ).setRegistryName(MODID, "red_sugar")));

  public static final AntsItem ANTS_ITEM = (AntsItem)((new AntsItem(
    TRAIL_BLOCK,
    default_item_properties().rarity(Rarity.UNCOMMON)
  ).setRegistryName(MODID, "ants")));

  private static final Item modItems[] = {
    RED_SUGAR_ITEM,
    ANTS_ITEM
  };

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entities and entities
  //--------------------------------------------------------------------------------------------------------------------

  public static final TileEntityType<?> TET_HIVE = TileEntityType.Builder
    .create(RedAntHive.RedAntHiveTileEntity::new, HIVE_BLOCK)
    .build(null)
    .setRegistryName(MODID, "te_hive");

  private static final TileEntityType<?> tile_entity_types[] = {
    TET_HIVE
  };

  @SuppressWarnings("all")
  private static final EntityType<?> entity_types[] = {
  };

  //--------------------------------------------------------------------------------------------------------------------
  // Containers
  //--------------------------------------------------------------------------------------------------------------------

  public static final ContainerType<RedAntHive.RedAntHiveContainer> CT_HIVE;

  static {
    CT_HIVE = (new ContainerType<RedAntHive.RedAntHiveContainer>(RedAntHive.RedAntHiveContainer::new));
    CT_HIVE.setRegistryName(MODID,"ct_hive");
  }

  @SuppressWarnings("all")
  private static final ContainerType<?> container_types[] = {
    CT_HIVE
  };

  //--------------------------------------------------------------------------------------------------------------------
  // Initialisation events
  //--------------------------------------------------------------------------------------------------------------------

  private final static List<Item> registeredItems = new ArrayList<>();

  public static List<Block> allBlocks()
  { return Arrays.asList(modBlocks); }

  public static List<Item> allItems()
  {
    if(!registeredItems.isEmpty()) return registeredItems;
    HashMap<ResourceLocation,Item> items = new HashMap<>();
    for(Item item:modItems) {
      items.put(item.getRegistryName(), item);
    }
    for(Block block:modBlocks) {
      ResourceLocation rl = block.getRegistryName();
      if(rl == null) continue;
      Item item;
      if(block instanceof StandardBlocks.IBlockItemFactory) {
        item = ((StandardBlocks.IBlockItemFactory)block).getBlockItem(block, (new BlockItem.Properties().group(ModAnthillInside.ITEMGROUP)));
      } else {
        item = new BlockItem(block, (new BlockItem.Properties().group(ModAnthillInside.ITEMGROUP)));
      }
      if((!items.containsValue(item)) && (!items.containsKey(item.getRegistryName())) ){
        items.put(rl, item.setRegistryName(rl));
      }
    }
    registeredItems.addAll(items.values());
    return registeredItems;
  }

  public static List<EntityType<?>> allEntityTypes()
  { return Arrays.asList(entity_types); }

  public static List<ContainerType<?>> allContainerTypes()
  { return Arrays.asList(container_types); }

  public static List<TileEntityType<?>> allTileEntityTypes()
  { return Arrays.asList(tile_entity_types); }

  @SuppressWarnings("deprecation")
  public static boolean isExperimentalBlock(Block block)
  { return false; }

  @Nonnull
  public static List<Block> getRegisteredBlocks()
  { return Collections.unmodifiableList(Arrays.asList(modBlocks)); }

  @Nonnull
  public static List<Item> getRegisteredItems()
  { return Collections.unmodifiableList(Arrays.asList(modItems)); }

  public static final void registerContainerGuis()
  {
    ScreenManager.registerFactory(CT_HIVE, RedAntHive.RedAntHiveGui::new);
  }

  @OnlyIn(Dist.CLIENT)
  @SuppressWarnings("unchecked")
  public static final void registerTileEntityRenderers()
  {}

  @OnlyIn(Dist.CLIENT)
  public static final void processContentClientSide()
  {
    // Block renderer selection
    for(Block block: getRegisteredBlocks()) {
      if(block instanceof IStandardBlock) {
        switch(((IStandardBlock)block).getRenderTypeHint()) {
          case CUTOUT:
            RenderTypeLookup.setRenderLayer(block, RenderType.getCutout());
            break;
          case CUTOUT_MIPPED:
            RenderTypeLookup.setRenderLayer(block, RenderType.getCutoutMipped());
            break;
          case TRANSLUCENT:
            RenderTypeLookup.setRenderLayer(block, RenderType.getTranslucent());
            break;
          case TRANSLUCENT_NO_CRUMBLING:
            RenderTypeLookup.setRenderLayer(block, RenderType.getTranslucentNoCrumbling());
            break;
          case SOLID:
            break;
        }
      }
    }
  }

}
