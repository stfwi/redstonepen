/*
 * @file ModContent.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.Item;
import net.minecraft.item.Rarity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.logging.log4j.Logger;
import wile.redstonepen.blocks.CircuitComponents;
import wile.redstonepen.blocks.ControlBox;
import wile.redstonepen.blocks.RedstoneTrack;
import wile.redstonepen.items.RedstonePenItem;
import wile.redstonepen.libmc.blocks.StandardBlocks;
import wile.redstonepen.libmc.detail.Auxiliaries;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class ModContent
{
  private static final Logger LOGGER = ModRedstonePen.LOGGER;
  private static final String MODID = ModRedstonePen.MODID;

  // -----------------------------------------------------------------------------------------------------------------
  // -- Blocks
  // -----------------------------------------------------------------------------------------------------------------

  public static final RedstoneTrack.RedstoneTrackBlock TRACK_BLOCK = (RedstoneTrack.RedstoneTrackBlock)(new RedstoneTrack.RedstoneTrackBlock(
    StandardBlocks.CFG_DEFAULT,
    AbstractBlock.Properties.of(Material.DECORATION).noCollission().instabreak().dynamicShape().randomTicks()
  )).setRegistryName(new ResourceLocation(MODID, "track"));

  public static final CircuitComponents.RelayBlock RELAY_BLOCK = (CircuitComponents.RelayBlock)(new CircuitComponents.RelayBlock(
    StandardBlocks.CFG_CUTOUT,
    AbstractBlock.Properties.of(Material.DECORATION).noCollission().instabreak(),
    Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
  )).setRegistryName(new ResourceLocation(MODID, "relay"));

  public static final CircuitComponents.InvertedRelayBlock INVERTED_RELAY_BLOCK = (CircuitComponents.InvertedRelayBlock)(new CircuitComponents.InvertedRelayBlock(
    StandardBlocks.CFG_CUTOUT,
    AbstractBlock.Properties.of(Material.DECORATION).noCollission().instabreak(),
    Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
  )).setRegistryName(new ResourceLocation(MODID, "inverted_relay"));

  public static final CircuitComponents.BistableRelayBlock BISTABLE_RELAY_BLOCK = (CircuitComponents.BistableRelayBlock)(new CircuitComponents.BistableRelayBlock(
    StandardBlocks.CFG_CUTOUT,
    AbstractBlock.Properties.of(Material.DECORATION).noCollission().instabreak(),
    Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
  )).setRegistryName(new ResourceLocation(MODID, "bistable_relay"));

  public static final CircuitComponents.PulseRelayBlock PULSE_RELAY_BLOCK = (CircuitComponents.PulseRelayBlock)(new CircuitComponents.PulseRelayBlock(
    StandardBlocks.CFG_CUTOUT,
    AbstractBlock.Properties.of(Material.DECORATION).noCollission().instabreak(),
    Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
  )).setRegistryName(new ResourceLocation(MODID, "pulse_relay"));

  public static final CircuitComponents.BridgeRelayBlock BRIDGE_RELAY_BLOCK = (CircuitComponents.BridgeRelayBlock)(new CircuitComponents.BridgeRelayBlock(
    StandardBlocks.CFG_CUTOUT,
    AbstractBlock.Properties.of(Material.DECORATION).noCollission().instabreak(),
    Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
  )).setRegistryName(new ResourceLocation(MODID, "bridge_relay"));

  public static final ControlBox.ControlBoxBlock CONTROLBOX_BLOCK = (ControlBox.ControlBoxBlock)(new ControlBox.ControlBoxBlock(
    StandardBlocks.CFG_CUTOUT,
    AbstractBlock.Properties.of(Material.DECORATION).noCollission().instabreak(),
    new AxisAlignedBB[]{
      Auxiliaries.getPixeledAABB(0,0,0, 16,2,16),
      Auxiliaries.getPixeledAABB(3,1,3, 13,3.9,13)
    }
  )).setRegistryName(new ResourceLocation(MODID, "control_box"));

  //--------------------------------------------------------------------------------------------------------------------
  // Items
  //--------------------------------------------------------------------------------------------------------------------

  public static final RedstonePenItem QUILL_ITEM = (RedstonePenItem)((new RedstonePenItem(
    (new Item.Properties()).tab(ModRedstonePen.ITEMGROUP).rarity(Rarity.UNCOMMON).stacksTo(1).defaultDurability(0)
  ).setRegistryName(MODID, "quill")));

  public static final RedstonePenItem PEN_ITEM = (RedstonePenItem)((new RedstonePenItem(
    (new Item.Properties()).tab(ModRedstonePen.ITEMGROUP).rarity(Rarity.UNCOMMON).stacksTo(0).defaultDurability(256)
  ).setRegistryName(MODID, "pen")));

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entities
  //--------------------------------------------------------------------------------------------------------------------

  public static final TileEntityType<?> TET_TRACK = TileEntityType.Builder
    .of(RedstoneTrack.TrackTileEntity::new, TRACK_BLOCK)
    .build(null)
    .setRegistryName(MODID, "te_track");

  public static final TileEntityType<?> TET_CONTROLBOX = TileEntityType.Builder
    .of(ControlBox.ControlBoxBlockEntity::new, CONTROLBOX_BLOCK)
    .build(null)
    .setRegistryName(MODID, "te_control_box");

  private static final TileEntityType<?> tile_entity_types[] = {
    TET_TRACK,
    TET_CONTROLBOX
  };

  //--------------------------------------------------------------------------------------------------------------------
  // Container registration
  //--------------------------------------------------------------------------------------------------------------------

  public static final ContainerType<ControlBox.ControlBoxUiContainer> CT_CONTROLBOX;
  static { CT_CONTROLBOX = (new ContainerType<>(ControlBox.ControlBoxUiContainer::new)); CT_CONTROLBOX.setRegistryName(MODID,"ct_control_box"); }
  private static final ContainerType<?>[] menu_types = { CT_CONTROLBOX };

  //--------------------------------------------------------------------------------------------------------------------
  // Initialisation events
  //--------------------------------------------------------------------------------------------------------------------

  public static List<Block> allBlocks()
  {
    List<Block> blocks = new ArrayList<>();
    blocks.add(TRACK_BLOCK);
    blocks.add(RELAY_BLOCK);
    blocks.add(INVERTED_RELAY_BLOCK);
    blocks.add(BISTABLE_RELAY_BLOCK);
    blocks.add(PULSE_RELAY_BLOCK);
    blocks.add(BRIDGE_RELAY_BLOCK);
    blocks.add(CONTROLBOX_BLOCK);
    return blocks;
  }

  public static List<Item> allItems()
  {
    final List<Item> items = new ArrayList<>();
    items.add(QUILL_ITEM);
    items.add(PEN_ITEM);
    items.add(new CircuitComponents.DirectedComponentBlockItem(RELAY_BLOCK, (new Item.Properties().tab(ModRedstonePen.ITEMGROUP))).setRegistryName("relay"));
    items.add(new CircuitComponents.DirectedComponentBlockItem(INVERTED_RELAY_BLOCK, (new Item.Properties().tab(ModRedstonePen.ITEMGROUP))).setRegistryName("inverted_relay"));
    items.add(new CircuitComponents.DirectedComponentBlockItem(BISTABLE_RELAY_BLOCK, (new Item.Properties().tab(ModRedstonePen.ITEMGROUP))).setRegistryName("bistable_relay"));
    items.add(new CircuitComponents.DirectedComponentBlockItem(PULSE_RELAY_BLOCK, (new Item.Properties().tab(ModRedstonePen.ITEMGROUP))).setRegistryName("pulse_relay"));
    items.add(new CircuitComponents.DirectedComponentBlockItem(BRIDGE_RELAY_BLOCK, (new Item.Properties().tab(ModRedstonePen.ITEMGROUP))).setRegistryName("bridge_relay"));
    items.add(new CircuitComponents.DirectedComponentBlockItem(CONTROLBOX_BLOCK, (new Item.Properties().tab(ModRedstonePen.ITEMGROUP))).setRegistryName("control_box"));
    return items;
  }

  @Nonnull
  public static List<Block> getRegisteredBlocks()
  { return Collections.unmodifiableList(allBlocks()); }

  @Nonnull
  public static List<Item> getRegisteredItems()
  { return Collections.unmodifiableList(allItems()); }

  @Nonnull
  public static List<TileEntityType<?>> allTileEntityTypes()
  { return Arrays.asList(tile_entity_types); }

  @Nonnull
  public static List<ContainerType<?>> allMenuTypes()
  { return Arrays.asList(menu_types); }

  @OnlyIn(Dist.CLIENT)
  public static void registerContainerGuis(final FMLClientSetupEvent event)
  { ScreenManager.register(CT_CONTROLBOX, ControlBox.ControlBoxGui::new); }

  @OnlyIn(Dist.CLIENT)
  public static final void processContentClientSide()
  { RenderTypeLookup.setRenderLayer(TRACK_BLOCK, RenderType.cutout()); }

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
