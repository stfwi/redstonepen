/*
 * @file ModContent.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ObjectHolder;
import wile.redstonepen.blocks.CircuitComponents;
import wile.redstonepen.blocks.ControlBox;
import wile.redstonepen.blocks.RedstoneTrack;
import wile.redstonepen.items.RedstonePenItem;
import wile.redstonepen.libmc.blocks.StandardBlocks;
import wile.redstonepen.libmc.detail.Auxiliaries;
import wile.redstonepen.libmc.detail.Registries;


public class ModContent
{
  private static class detail {
    public static String MODID = "";
  }

  public static void init(String modid)
  {
    detail.MODID = modid;
    initBlocks();
    initItems();
  }

  public static void initBlocks()
  {
    Registries.addBlock("track",
      ()->new RedstoneTrack.RedstoneTrackBlock(
        StandardBlocks.CFG_DEFAULT,
        BlockBehaviour.Properties.of(Material.DECORATION).noCollission().instabreak().dynamicShape().randomTicks()
      ),
      RedstoneTrack.TrackTileEntity::new
    );
    Registries.addBlock("relay",
      ()->new CircuitComponents.RelayBlock(
        StandardBlocks.CFG_CUTOUT,
        BlockBehaviour.Properties.of(Material.DECORATION).noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      )
    );
    Registries.addBlock("inverted_relay",
      ()->new CircuitComponents.InvertedRelayBlock(
        StandardBlocks.CFG_CUTOUT,
        BlockBehaviour.Properties.of(Material.DECORATION).noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      )
    );
    Registries.addBlock("bistable_relay",
      ()->new CircuitComponents.BistableRelayBlock(
        StandardBlocks.CFG_CUTOUT,
        BlockBehaviour.Properties.of(Material.DECORATION).noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      )
    );
    Registries.addBlock("pulse_relay",
      ()->new CircuitComponents.PulseRelayBlock(
        StandardBlocks.CFG_CUTOUT,
        BlockBehaviour.Properties.of(Material.DECORATION).noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      )
    );
    Registries.addBlock("bridge_relay",
      ()->new CircuitComponents.BridgeRelayBlock(
        StandardBlocks.CFG_CUTOUT,
        BlockBehaviour.Properties.of(Material.DECORATION).noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      )
    );
    Registries.addBlock("control_box",
      ()->new ControlBox.ControlBoxBlock(
        StandardBlocks.CFG_CUTOUT,
        BlockBehaviour.Properties.of(Material.DECORATION).noCollission().instabreak(),
        new AABB[]{
          Auxiliaries.getPixeledAABB(0,0,0, 16,2,16),
          Auxiliaries.getPixeledAABB(3,1,3, 13,3.9,13)
        }
      ),
      ControlBox.ControlBoxBlockEntity::new,
      ControlBox.ControlBoxUiContainer::new
    );
  }

  public static void initItems()
  {
    Registries.addItem("quill", ()->new RedstonePenItem(
      (new Item.Properties()).tab(Registries.getCreativeModeTab()).rarity(Rarity.UNCOMMON).stacksTo(1).defaultDurability(0)
    ));
    Registries.addItem("pen", ()->new RedstonePenItem(
      (new Item.Properties()).tab(Registries.getCreativeModeTab()).rarity(Rarity.UNCOMMON).stacksTo(0).defaultDurability(256)
    ));
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Initialisation events
  //--------------------------------------------------------------------------------------------------------------------

  @OnlyIn(Dist.CLIENT)
  @SuppressWarnings("unchecked")
  public static void registerMenuGuis(final FMLClientSetupEvent event)
  { MenuScreens.register((MenuType<ControlBox.ControlBoxUiContainer>)Registries.getMenuTypeOfBlock("control_box"), ControlBox.ControlBoxGui::new); }

  @OnlyIn(Dist.CLIENT)
  public static void processContentClientSide()
  { ItemBlockRenderTypes.setRenderLayer(Registries.getBlock("track")  , RenderType.cutout()); }

  @OnlyIn(Dist.CLIENT)
  public static void registerModels()
  { wile.redstonepen.detail.ModRenderers.TrackTer.registerModels(); }

  @OnlyIn(Dist.CLIENT)
  @SuppressWarnings("unchecked")
  public static void registerBlockEntityRenderers(final FMLClientSetupEvent event)
  {
    BlockEntityRenderers.register(
      (BlockEntityType<RedstoneTrack.TrackTileEntity>)Registries.getBlockEntityTypeOfBlock("track"),
      wile.redstonepen.detail.ModRenderers.TrackTer::new
    );
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Accessors
  //--------------------------------------------------------------------------------------------------------------------

  public static MenuType<?> getMenuTypeOfBlock(String block_name)
  { return Registries.getMenuTypeOfBlock(block_name); }

  public static BlockEntityType<?> getBlockEntityTypeOfBlock(String block_name)
  { return Registries.getBlockEntityTypeOfBlock(block_name); }

  public static final class references
  {
    @ObjectHolder("redstonepen:track") public static final RedstoneTrack.RedstoneTrackBlock TRACK_BLOCK = null;
    @ObjectHolder("redstonepen:bridge_relay") public static final CircuitComponents.BridgeRelayBlock BRIDGE_RELAY_BLOCK = null;
    @ObjectHolder("redstonepen:control_box") public static final ControlBox.ControlBoxBlock CONTROLBOX_BLOCK = null;





  }

}
