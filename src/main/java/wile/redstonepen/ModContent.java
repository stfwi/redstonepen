/*
 * @file ModContent.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.AABB;
import wile.redstonepen.blocks.CircuitComponents;
import wile.redstonepen.blocks.ControlBox;
import wile.redstonepen.blocks.RedstoneTrack;
import wile.redstonepen.items.RedstonePenItem;
import wile.redstonepen.libmc.StandardBlocks;
import wile.redstonepen.libmc.Auxiliaries;
import wile.redstonepen.libmc.Registries;


public class ModContent
{
  public static void init()
  {
    initBlocks();
    initItems();
    Registries.addRecipeSerializer("crafting_extended_shapeless", ()->wile.redstonepen.libmc.ExtendedShapelessRecipe.SERIALIZER);
  }

  public static void initBlocks()
  {
    Registries.addBlock("track",
      ()->new RedstoneTrack.RedstoneTrackBlock(
        StandardBlocks.CFG_DEFAULT,
        BlockBehaviour.Properties.of().noCollission().instabreak().dynamicShape().randomTicks()
      ),
      RedstoneTrack.TrackBlockEntity::new
    );
    Registries.addBlock("relay",
      ()->new CircuitComponents.RelayBlock(
        StandardBlocks.CFG_CUTOUT,
        BlockBehaviour.Properties.of().noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      ),
      CircuitComponents.DirectedComponentBlockItem::new
    );
    Registries.addBlock("inverted_relay",
      ()->new CircuitComponents.InvertedRelayBlock(
        StandardBlocks.CFG_CUTOUT,
        BlockBehaviour.Properties.of().noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      ),
      CircuitComponents.DirectedComponentBlockItem::new
    );
    Registries.addBlock("bistable_relay",
      ()->new CircuitComponents.BistableRelayBlock(
        StandardBlocks.CFG_CUTOUT,
        BlockBehaviour.Properties.of().noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      ),
      CircuitComponents.DirectedComponentBlockItem::new
    );
    Registries.addBlock("pulse_relay",
      ()->new CircuitComponents.PulseRelayBlock(
        StandardBlocks.CFG_CUTOUT,
        BlockBehaviour.Properties.of().noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      ),
      CircuitComponents.DirectedComponentBlockItem::new
    );
    Registries.addBlock("bridge_relay",
      ()->new CircuitComponents.BridgeRelayBlock(
        StandardBlocks.CFG_CUTOUT,
        BlockBehaviour.Properties.of().noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      ),
      CircuitComponents.DirectedComponentBlockItem::new
    );
    Registries.addBlock("control_box",
      ()->new ControlBox.ControlBoxBlock(
        StandardBlocks.CFG_CUTOUT,
        BlockBehaviour.Properties.of().noCollission().instabreak(),
        new AABB[]{
          Auxiliaries.getPixeledAABB(0,0,0, 16,2,16),
          Auxiliaries.getPixeledAABB(3,1,3, 13,3.9,13)
        }
      ),
      CircuitComponents.DirectedComponentBlockItem::new,
      ControlBox.ControlBoxBlockEntity::new,
      ControlBox.ControlBoxUiContainer::new
    );
  }

  public static void initItems()
  {
    Registries.addItem("quill", ()->new RedstonePenItem(
      (new Item.Properties()).rarity(Rarity.UNCOMMON).stacksTo(64).defaultDurability(0)
    ));
    Registries.addItem("pen", ()->new RedstonePenItem(
      (new Item.Properties()).rarity(Rarity.UNCOMMON).stacksTo(1).defaultDurability(256)
    ));
  }

  public static void initReferences(String registry_name)
  {
    switch(registry_name) {
      case "minecraft:block" -> {
        references.TRACK_BLOCK = (RedstoneTrack.RedstoneTrackBlock)Registries.getBlock("track");
        references.BRIDGE_RELAY_BLOCK = (CircuitComponents.BridgeRelayBlock)Registries.getBlock("bridge_relay");
        references.CONTROLBOX_BLOCK = (ControlBox.ControlBoxBlock)Registries.getBlock("control_box");
      }
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Accessors
  //--------------------------------------------------------------------------------------------------------------------

  public static MenuType<?> getMenuTypeOfBlock(String block_name)
  { return Registries.getMenuTypeOfBlock(block_name); }

  public static MenuType<?> getMenuTypeOfBlock(Block block)
  { return Registries.getMenuTypeOfBlock(block); }

  public static BlockEntityType<?> getBlockEntityTypeOfBlock(String block_name)
  { return Registries.getBlockEntityTypeOfBlock(block_name); }

  public static BlockEntityType<?> getBlockEntityTypeOfBlock(Block block)
  { return Registries.getBlockEntityTypeOfBlock(block); }

  public static final class references
  {
    public static RedstoneTrack.RedstoneTrackBlock TRACK_BLOCK = null;
    public static CircuitComponents.BridgeRelayBlock BRIDGE_RELAY_BLOCK = null;
    public static ControlBox.ControlBoxBlock CONTROLBOX_BLOCK = null;
  }

}
