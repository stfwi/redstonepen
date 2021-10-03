/*
 * @file ModRedstonePen.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
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
  public static final boolean USE_CONFIG = false;

  public ModRedstonePen()
  {
    Auxiliaries.init(MODID, LOGGER, ModConfig::getServerConfig);
    Auxiliaries.logGitVersion(MODNAME);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onSetup);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    if(USE_CONFIG) ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.COMMON_CONFIG_SPEC);
    MinecraftForge.EVENT_BUS.register(this);
  }

  public static final Logger logger() { return LOGGER; }

  // -------------------------------------------------------------------------------------------------------------------
  // Events
  // -------------------------------------------------------------------------------------------------------------------

  private void onSetup(final FMLCommonSetupEvent event)
  {
    wile.redstonepen.libmc.detail.Networking.init(MODID);
    ModConfig.apply();
  }

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
    public static void onTileEntityRegistry(final RegistryEvent.Register<BlockEntityType<?>> event)
    { ModContent.allTileEntityTypes().forEach(e->event.getRegistry().register(e)); }

    @SubscribeEvent
    public static void onRegisterModels(final ModelRegistryEvent event)
    { ModContent.registerModels(); }

    @SubscribeEvent
    public static void onRecipeRegistry(final RegistryEvent.Register<RecipeSerializer<?>> event)
    { event.getRegistry().register(wile.redstonepen.libmc.detail.ExtendedShapelessRecipe.SERIALIZER); }

//    public static void onConfigLoad(net.minecraftforge.fml.config.ModConfig.Loading configEvent)
//    { ModConfig.apply(); }
//
//    public static void onConfigReload(net.minecraftforge.fml.config.ModConfig.Reloading configEvent)
//    {
//      try {
//        logger().info("Config file changed {}", configEvent.getConfig().getFileName());
//        ModConfig.apply();
//      } catch(Throwable e) {
//        logger().error("Failed to load changed config: " + e.getMessage());
//      }
//    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Item group / creative tab
  // -------------------------------------------------------------------------------------------------------------------

  public static final CreativeModeTab ITEMGROUP = (new CreativeModeTab("tab" + MODID) {
    @OnlyIn(Dist.CLIENT)
    public ItemStack makeIcon()
    { return new ItemStack(ModContent.QUILL_ITEM); }
  });
}
