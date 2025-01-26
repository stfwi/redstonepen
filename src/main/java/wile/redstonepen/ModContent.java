/*
 * @file ModContent.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import wile.redstonepen.blocks.*;
import wile.redstonepen.items.RedstonePenItem;
import wile.redstonepen.items.RemoteItem;
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
    final BlockBehaviour.StateArgumentPredicate<EntityType<?>> never = (w,s,p,e)->false;

    Registries.addBlock("track",
      (properties)->new RedstoneTrack.RedstoneTrackBlock(
        StandardBlocks.CFG_DEFAULT,
        properties.noCollission().instabreak().dynamicShape().randomTicks().noLootTable()
      ),
      RedstoneTrack.TrackBlockEntity::new
    );
    Registries.addBlock("control_box",
      (properties)->new ControlBox.ControlBoxBlock(
        StandardBlocks.CFG_CUTOUT,
        properties.noCollission().instabreak().noLootTable(),
        new AABB[]{
          Auxiliaries.getPixeledAABB(0,0,0, 16,2,16),
          Auxiliaries.getPixeledAABB(3,1,3, 13,3.9,13)
        }
      ),
      CircuitComponents.DirectedComponentBlockItem::new,
      ControlBox.ControlBoxBlockEntity::new,
      ControlBox.ControlBoxUiContainer::new
    );
    Registries.addBlock("relay",
      (properties)->new CircuitComponents.RelayBlock(
        StandardBlocks.CFG_CUTOUT,
        properties.noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      ),
      CircuitComponents.DirectedComponentBlockItem::new
    );
    Registries.addBlock("inverted_relay",
      (properties)->new CircuitComponents.InvertedRelayBlock(
        StandardBlocks.CFG_CUTOUT,
        properties.noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      ),
      CircuitComponents.DirectedComponentBlockItem::new
    );
    Registries.addBlock("pulse_relay",
      (properties)->new CircuitComponents.PulseRelayBlock(
        StandardBlocks.CFG_CUTOUT,
        properties.noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      ),
      CircuitComponents.DirectedComponentBlockItem::new
    );
    Registries.addBlock("bistable_relay",
      (properties)->new CircuitComponents.BistableRelayBlock(
        StandardBlocks.CFG_CUTOUT,
        properties.noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      ),
      CircuitComponents.DirectedComponentBlockItem::new
    );
    Registries.addBlock("bridge_relay",
      (properties)->new CircuitComponents.BridgeRelayBlock(
        StandardBlocks.CFG_CUTOUT,
        properties.noCollission().instabreak(),
        Auxiliaries.getPixeledAABB(5,0,0, 11,1,16)
      ),
      CircuitComponents.DirectedComponentBlockItem::new
    );
    Registries.addBlock("basic_gauge",
      (properties)->new BasicGauge.BasicGaugeBlock(
        StandardBlocks.CFG_TRANSLUCENT,
        properties.isValidSpawn(never).strength(0.3f).sound(SoundType.COPPER).noCollission().lightLevel((s)->3)
      )
    );
    Registries.addBlock("basic_lever",
      (properties)->new BasicLever.BasicLeverBlock(
        new BasicLever.BasicLeverBlock.Config(0.8f, 0.9f),
        properties.noCollission().isValidSpawn(never).strength(0.3f).sound(SoundType.METAL).pushReaction(PushReaction.DESTROY)
      )
    );
    Registries.addBlock("basic_button",
      (properties)->new BasicButton.BasicButtonBlock(
        new BasicButton.BasicButtonBlock.Config(0.8f, 0.9f, 20),
        properties.noCollission().isValidSpawn(never).strength(0.3f).sound(SoundType.METAL).pushReaction(PushReaction.DESTROY)
      )
    );
    Registries.addBlock("basic_pulse_button",
      (properties)->new BasicButton.BasicButtonBlock(
        new BasicButton.BasicButtonBlock.Config(0.8f, 0.9f, 2),
        properties.noCollission().isValidSpawn(never).strength(0.3f).sound(SoundType.METAL).pushReaction(PushReaction.DESTROY)
      )
    );
  }

  public static void initItems()
  {
    Registries.addItem("pen", (properties)->new RedstonePenItem(
      properties.stacksTo(0).durability(256)
    ));
    Registries.addItem("quill", (properties)->new RedstonePenItem(
      properties.stacksTo(1).durability(0)
    ));
    Registries.addItem("remote", (properties)->new RemoteItem(
      properties.stacksTo(1).durability(1)
    ));
  }

  public static void initReferences()
  {
    references.TRACK_BLOCK = (RedstoneTrack.RedstoneTrackBlock)Registries.getBlock("track");
    references.BRIDGE_RELAY_BLOCK = (CircuitComponents.BridgeRelayBlock)Registries.getBlock("bridge_relay");
    references.CONTROLBOX_BLOCK = (ControlBox.ControlBoxBlock)Registries.getBlock("control_box");
    references.BASIC_GAUGE_BLOCK = (BasicGauge.BasicGaugeBlock)Registries.getBlock("basic_gauge");
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
    public static BasicGauge.BasicGaugeBlock BASIC_GAUGE_BLOCK = null;
  }

}
