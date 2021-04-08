/*
 * @file ModContent.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.item.Rarity;
import net.minecraft.block.material.Material;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.logging.log4j.Logger;
import wile.redstonepen.blocks.*;
import wile.redstonepen.items.*;
import wile.redstonepen.libmc.blocks.StandardBlocks;

import javax.annotation.Nonnull;
import java.util.*;


public class ModContent
{
  private static final Logger LOGGER = ModRedstonePen.LOGGER;
  private static final String MODID = ModRedstonePen.MODID;

  // -----------------------------------------------------------------------------------------------------------------
  // -- Blocks
  // -----------------------------------------------------------------------------------------------------------------

  public static final RedstoneTrack.RedstoneTrackBlock TRACK_BLOCK = (RedstoneTrack.RedstoneTrackBlock)(new RedstoneTrack.RedstoneTrackBlock(
    StandardBlocks.CFG_DEFAULT,
    Block.Properties.create(Material.MISCELLANEOUS).doesNotBlockMovement().zeroHardnessAndResistance().variableOpacity().tickRandomly()
  )).setRegistryName(new ResourceLocation(MODID, "track"));

  private static final Block modBlocks[] = {
    TRACK_BLOCK
  };

  //--------------------------------------------------------------------------------------------------------------------
  // Items
  //--------------------------------------------------------------------------------------------------------------------

  public static final RedstonePenItem QUILL_ITEM = (RedstonePenItem)((new RedstonePenItem(
    (new Item.Properties()).group(ModRedstonePen.ITEMGROUP).rarity(Rarity.UNCOMMON).maxStackSize(1).defaultMaxDamage(0)
  ).setRegistryName(MODID, "quill")));

  public static final RedstonePenItem PEN_ITEM = (RedstonePenItem)((new RedstonePenItem(
    (new Item.Properties()).group(ModRedstonePen.ITEMGROUP).rarity(Rarity.UNCOMMON).maxStackSize(0).defaultMaxDamage(256)
  ).setRegistryName(MODID, "pen")));

  private static final Item modItems[] = {
    QUILL_ITEM,
    PEN_ITEM,
  };

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entities and entities
  //--------------------------------------------------------------------------------------------------------------------

  public static final TileEntityType<?> TET_TRACK = TileEntityType.Builder
    .create(RedstoneTrack.TrackTileEntity::new, TRACK_BLOCK)
    .build(null)
    .setRegistryName(MODID, "te_track");

  private static final TileEntityType<?> tile_entity_types[] = {
    TET_TRACK
  };

  //--------------------------------------------------------------------------------------------------------------------
  // Initialisation events
  //--------------------------------------------------------------------------------------------------------------------

  public static List<Block> allBlocks()
  { return Arrays.asList(modBlocks); }

  public static List<Item> allItems()
  { return Arrays.asList(modItems); }

  @Nonnull
  public static List<Block> getRegisteredBlocks()
  { return Collections.unmodifiableList(allBlocks()); }

  @Nonnull
  public static List<Item> getRegisteredItems()
  { return Collections.unmodifiableList(allItems()); }

  @Nonnull
  public static List<TileEntityType<?>> allTileEntityTypes()
  { return Arrays.asList(tile_entity_types); }

  @OnlyIn(Dist.CLIENT)
  public static final void processContentClientSide()
  { RenderTypeLookup.setRenderLayer(TRACK_BLOCK, RenderType.getCutout()); }

  @OnlyIn(Dist.CLIENT)
  public static void registerModels()
  { wile.redstonepen.detail.ModRenderers.TrackTer.registerModels(); }

  @OnlyIn(Dist.CLIENT)
  @SuppressWarnings("unchecked")
  public static final void registerTileEntityRenderers(final FMLClientSetupEvent event)
  {
    ClientRegistry.bindTileEntityRenderer(
      (TileEntityType<RedstoneTrack.TrackTileEntity>)TET_TRACK,
      wile.redstonepen.detail.ModRenderers.TrackTer::new
    );
  }

}
