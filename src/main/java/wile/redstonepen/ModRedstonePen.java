/*
 * @file ModRedstonePen.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import wile.redstonepen.libmc.detail.Auxiliaries;
import wile.redstonepen.libmc.detail.Overlay;
import wile.redstonepen.libmc.detail.Registries;


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
    Registries.init(MODID, "quill");
    ModContent.init(MODID);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onSetup);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    if(USE_CONFIG) ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.COMMON_CONFIG_SPEC);
    MinecraftForge.EVENT_BUS.register(this);
  }

  public static Logger logger() { return LOGGER; }

  // -------------------------------------------------------------------------------------------------------------------
  // Events
  // -------------------------------------------------------------------------------------------------------------------

  private void onSetup(final FMLCommonSetupEvent event)
  {
    wile.redstonepen.libmc.detail.Networking.init(MODID);
    ModConfig.apply();
    wile.redstonepen.detail.RcaSync.CommonRca.init();
  }

  private void onClientSetup(final FMLClientSetupEvent event)
  {
    Overlay.register();
    ModContent.registerBlockEntityRenderers(event);
    ModContent.registerMenuGuis(event);
    ModContent.processContentClientSide();
    Overlay.TextOverlayGui.on_config(
      0.75,
      0x00ffaa00,
      0x55333333,
      0x55333333,
      0x55444444
    );
    wile.redstonepen.detail.RcaSync.ClientRca.init();
    MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ForgeEvents::onPlayerTickEvent);
  }

  @Mod.EventBusSubscriber(bus=Mod.EventBusSubscriber.Bus.MOD)
  public static final class ForgeEvents
  {
    @SubscribeEvent
    public static void onBlockRegistry(final RegistryEvent.Register<Block> event)
    { Registries.onBlockRegistry((rl, block)->event.getRegistry().register(block)); }

    @SubscribeEvent
    public static void onItemRegistry(final RegistryEvent.Register<Item> event)
    { Registries.onItemRegistry((rl, item)->event.getRegistry().register(item)); }

    @SubscribeEvent
    public static void onBlockEntityRegistry(final RegistryEvent.Register<BlockEntityType<?>> event)
    { Registries.onBlockEntityRegistry((rl, tet)->event.getRegistry().register(tet)); }

    @SubscribeEvent
    public static void onRegisterMenuTypes(final RegistryEvent.Register<MenuType<?>> event)
    { Registries.onMenuTypeRegistry((rl, ct)->event.getRegistry().register(ct)); }

    @SubscribeEvent
    public static void onRegisterModels(final ModelRegistryEvent event)
    { ModContent.registerModels(); }

    @SubscribeEvent
    public static void onRecipeRegistry(final RegistryEvent.Register<RecipeSerializer<?>> event)
    { event.getRegistry().register(wile.redstonepen.libmc.detail.ExtendedShapelessRecipe.SERIALIZER); }

    public static void onPlayerTickEvent(final TickEvent.PlayerTickEvent event)
    {
      if((event.phase != TickEvent.Phase.END) || (!event.player.level.isClientSide)) return;
      if((event.player.level.getGameTime() & 0x1) != 0) return;
      wile.redstonepen.detail.RcaSync.ClientRca.tick();
    }
  }
}
