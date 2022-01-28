/*
 * @file ModContent.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.logging.log4j.Logger;
import wile.redstonepen.blocks.*;
import wile.redstonepen.items.*;
import wile.redstonepen.libmc.blocks.StandardBlocks;
import wile.redstonepen.libmc.detail.Auxiliaries;

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
    BlockBehaviour.Properties.of(Material.DECORATION).noCollission().instabreak().dynamicShape().randomTicks()
  )).setRegistryName(new ResourceLocation(MODID, "track"));

  public static final CircuitComponents.RelayBlock RELAY_BLOCK = (CircuitComponents.RelayBlock)(new CircuitComponents.RelayBlock(
    StandardBlocks.CFG_CUTOUT,
    BlockBehaviour.Properties.of(Material.DECORATION).noCollission().instabreak(),
    Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
  )).setRegistryName(new ResourceLocation(MODID, "relay"));

  public static final CircuitComponents.InvertedRelayBlock INVERTED_RELAY_BLOCK = (CircuitComponents.InvertedRelayBlock)(new CircuitComponents.InvertedRelayBlock(
    StandardBlocks.CFG_CUTOUT,
    BlockBehaviour.Properties.of(Material.DECORATION).noCollission().instabreak(),
    Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
  )).setRegistryName(new ResourceLocation(MODID, "inverted_relay"));

  public static final CircuitComponents.BistableRelayBlock BISTABLE_RELAY_BLOCK = (CircuitComponents.BistableRelayBlock)(new CircuitComponents.BistableRelayBlock(
    StandardBlocks.CFG_CUTOUT,
    BlockBehaviour.Properties.of(Material.DECORATION).noCollission().instabreak(),
    Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
  )).setRegistryName(new ResourceLocation(MODID, "bistable_relay"));

  public static final CircuitComponents.PulseRelayBlock PULSE_RELAY_BLOCK = (CircuitComponents.PulseRelayBlock)(new CircuitComponents.PulseRelayBlock(
    StandardBlocks.CFG_CUTOUT,
    BlockBehaviour.Properties.of(Material.DECORATION).noCollission().instabreak(),
    Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
  )).setRegistryName(new ResourceLocation(MODID, "pulse_relay"));

  public static final CircuitComponents.BridgeRelayBlock BRIDGE_RELAY_BLOCK = (CircuitComponents.BridgeRelayBlock)(new CircuitComponents.BridgeRelayBlock(
    StandardBlocks.CFG_CUTOUT,
    BlockBehaviour.Properties.of(Material.DECORATION).noCollission().instabreak(),
    Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
  )).setRegistryName(new ResourceLocation(MODID, "bridge_relay"));

  public static final ControlBox.ControlBoxBlock CONTROLBOX_BLOCK = (ControlBox.ControlBoxBlock)(new ControlBox.ControlBoxBlock(
    StandardBlocks.CFG_CUTOUT,
    BlockBehaviour.Properties.of(Material.DECORATION).noCollission().instabreak(),
    new AABB[]{
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
  // Tile entities and entities
  //--------------------------------------------------------------------------------------------------------------------

  public static final BlockEntityType<?> TET_TRACK = BlockEntityType.Builder
    .of(RedstoneTrack.TrackTileEntity::new, TRACK_BLOCK)
    .build(null)
    .setRegistryName(MODID, "te_track");

  private static final BlockEntityType<?>[] tile_entity_types = {
    TET_TRACK
  };

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
    //blocks.add(CONTROLBOX_BLOCK);
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
    //items.add(new BlockItem(CONTROLBOX_BLOCK, (new BlockItem.Properties().group(ModRedstonePen.ITEMGROUP))).setRegistryName("control_box"));
    return items;
  }

  @Nonnull
  public static List<Block> getRegisteredBlocks()
  { return Collections.unmodifiableList(allBlocks()); }

  @Nonnull
  public static List<Item> getRegisteredItems()
  { return Collections.unmodifiableList(allItems()); }

  @Nonnull
  public static List<BlockEntityType<?>> allTileEntityTypes()
  { return Arrays.asList(tile_entity_types); }

  @OnlyIn(Dist.CLIENT)
  public static void processContentClientSide()
  { ItemBlockRenderTypes.setRenderLayer(TRACK_BLOCK, RenderType.cutout()); }

  @OnlyIn(Dist.CLIENT)
  public static void registerModels()
  { wile.redstonepen.detail.ModRenderers.TrackTer.registerModels(); }

  @OnlyIn(Dist.CLIENT)
  @SuppressWarnings("unchecked")
  public static void registerTileEntityRenderers(final FMLClientSetupEvent event)
  {
    BlockEntityRenderers.register(
      (BlockEntityType<RedstoneTrack.TrackTileEntity>)TET_TRACK,
      wile.redstonepen.detail.ModRenderers.TrackTer::new
    );
  }

}
