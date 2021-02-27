/*
 * @file ModAnthillInside.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.minecraft.entity.EntityType;
import net.minecraft.block.Block;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.ItemGroup;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.server.ServerWorld;
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
import wile.redstonepen.blocks.RedAntHive;
import wile.redstonepen.libmc.detail.*;


@Mod("redstonepen")
public class ModAnthillInside
{
  public static final String MODID = "redstonepen";
  public static final String MODNAME = "Anthill Inside";
  public static final Logger LOGGER = LogManager.getLogger();

  public ModAnthillInside()
  {
    Auxiliaries.init(MODID, LOGGER, ModConfig::getServerConfig);
    Auxiliaries.logGitVersion(MODNAME);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onSetup);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(ForgeEvents::onConfigLoad);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(ForgeEvents::onConfigReload);
    ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.COMMON_CONFIG_SPEC);
    MinecraftForge.EVENT_BUS.register(this);
    MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onBlockBroken);
  }

  public static final Logger logger() { return LOGGER; }

  // -------------------------------------------------------------------------------------------------------------------
  // Events
  // -------------------------------------------------------------------------------------------------------------------

  private void onSetup(final FMLCommonSetupEvent event)
  {
    wile.redstonepen.libmc.detail.Networking.init(MODID);
  }

  private void onClientSetup(final FMLClientSetupEvent event)
  {
    wile.redstonepen.libmc.detail.Overlay.register();
    ModContent.registerTileEntityRenderers();
    ModContent.registerContainerGuis();
    ModContent.processContentClientSide();
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
    public static void onRegisterEntityTypes(final RegistryEvent.Register<EntityType<?>> event)
    { ModContent.allEntityTypes().forEach(e->event.getRegistry().register(e)); }

    @SubscribeEvent
    public static void onRegisterContainerTypes(final RegistryEvent.Register<ContainerType<?>> event)
    { ModContent.allContainerTypes().forEach(e->event.getRegistry().register(e)); }

    public static void onConfigLoad(net.minecraftforge.fml.config.ModConfig.Loading configEvent)
    { ModConfig.apply(); }

    public static void onConfigReload(net.minecraftforge.fml.config.ModConfig.Reloading configEvent)
    {
      try {
        logger().info("Config file changed {}", configEvent.getConfig().getFileName());
        ModConfig.apply();
      } catch(Throwable e) {
        logger().error("Failed to load changed config: " + e.getMessage());
      }
    }

    public static void onBlockBroken(net.minecraftforge.event.world.BlockEvent.BreakEvent event)
    {
      if((event.getState()==null) || (event.getPlayer()==null)) return;
      RedAntHive.onGlobalPlayerBlockBrokenEvent(event.getState(), (ServerWorld)event.getWorld(), event.getPos(), event.getPlayer());
    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Item group / creative tab
  // -------------------------------------------------------------------------------------------------------------------

  public static final ItemGroup ITEMGROUP = (new ItemGroup("tab" + MODID) {
    @OnlyIn(Dist.CLIENT)
    public ItemStack createIcon()
    { return new ItemStack(ModContent.ANTS_ITEM); }
  });
}
