/*
 * @file ModRedstonePen.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.minecraft.block.Block;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import wile.redstonepen.libmc.detail.*;


@Mod("redstonepen")
public class ModRedstonePen
{
  public static final String MODID = "redstonepen";
  public static final String MODNAME = "Redstone Pen";
  public static final Logger LOGGER = LogManager.getLogger();

  public ModRedstonePen()
  {
    Auxiliaries.init(MODID, LOGGER, ()->new CompoundNBT());
    Auxiliaries.logGitVersion(MODNAME);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onSetup);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    MinecraftForge.EVENT_BUS.register(this);
  }

  public static final Logger logger() { return LOGGER; }

  // -------------------------------------------------------------------------------------------------------------------
  // Events
  // -------------------------------------------------------------------------------------------------------------------

  private void onSetup(final FMLCommonSetupEvent event)
  { wile.redstonepen.libmc.detail.Networking.init(MODID); }

  private void onClientSetup(final FMLClientSetupEvent event)
  {
    Overlay.register();
    ModContent.registerTileEntityRenderers(event);
    ModContent.processContentClientSide();
    Overlay.TextOverlayGui.on_config(
      0.75,
      0x00ffaa00,
      0x55333333,
      0x55333333,
      0x55444444
    );
  }

  @Mod.EventBusSubscriber(bus=Mod.EventBusSubscriber.Bus.MOD)
  public static final class ForgeEvents
  {
    @SubscribeEvent
    public static void onBlocksRegistry(final RegistryEvent.Register<Block> event)
    { ModContent.allBlocks().forEach(e->event.getRegistry().register(e)); }

    @SubscribeEvent
    public static void onItemRegistry(final RegistryEvent.Register<Item> event)
    { ModContent.allItems().forEach(e->event.getRegistry().register(e)); }

    @SubscribeEvent
    public static void onTileEntityRegistry(final RegistryEvent.Register<TileEntityType<?>> event)
    { ModContent.allTileEntityTypes().forEach(e->event.getRegistry().register(e)); }

    @SubscribeEvent
    public static final void onRegisterModels(final ModelRegistryEvent event)
    { ModContent.registerModels(); }

    @SubscribeEvent
    public static final void onRecipeRegistry(final RegistryEvent.Register<IRecipeSerializer<?>> event)
    { event.getRegistry().register(wile.redstonepen.libmc.detail.ExtendedShapelessRecipe.SERIALIZER); }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Item group / creative tab
  // -------------------------------------------------------------------------------------------------------------------

  public static final ItemGroup ITEMGROUP = (new ItemGroup("tab" + MODID) {
    @OnlyIn(Dist.CLIENT)
    public ItemStack createIcon()
    { return new ItemStack(ModContent.PEN_ITEM); }
  });
}
