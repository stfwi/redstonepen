/*
 * @file ModRedstonePen.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import wile.redstonepen.libmc.Auxiliaries;
import wile.redstonepen.libmc.Networking;
import wile.redstonepen.libmc.Overlay;
import wile.redstonepen.libmc.Registries;


@Mod("redstonepen")
public class ModRedstonePen
{
  public static final String MODID = "redstonepen";
  public static final String MODNAME = "Redstone Pen";
  public static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
  public static final boolean USE_CONFIG = false;

  public ModRedstonePen()
  {
    Auxiliaries.init(MODID, LOGGER, ModConfig::getServerConfig);
    Auxiliaries.logGitVersion(MODNAME);
    Registries.init(MODID, "quill", (reg)->reg.register(FMLJavaModLoadingContext.get().getModEventBus()));
    ModContent.init(MODID);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onSetup);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onRegisterModels);
    if(USE_CONFIG) ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.COMMON_CONFIG_SPEC);
    MinecraftForge.EVENT_BUS.register(this);
  }

  public static Logger logger() { return LOGGER; }

  // -------------------------------------------------------------------------------------------------------------------
  // Events
  // -------------------------------------------------------------------------------------------------------------------

  private void onSetup(final FMLCommonSetupEvent event)
  {
    wile.redstonepen.libmc.Networking.init(MODID);
    ModConfig.apply();
    wile.redstonepen.detail.RcaSync.CommonRca.init();
  }

  private void onClientSetup(final FMLClientSetupEvent event)
  {
    Networking.OverlayTextMessage.setHandler(Overlay.TextOverlayGui::show);
    ModContent.registerMenuGuis(event);
    ModContent.registerBlockEntityRenderers();
    ModContent.processContentClientSide();
    Overlay.TextOverlayGui.on_config(
      0.75,
      0x00ffaa00,
      0x55333333,
      0x55333333,
      0x55444444
    );
    if(wile.redstonepen.detail.RcaSync.ClientRca.init()) {
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ModRedstonePen::onPlayerTickEvent);
    }
  }

  private void onRegisterModels(final ModelEvent.RegisterAdditional event)
  {
    wile.redstonepen.detail.ModRenderers.TrackTer.registerModels().forEach(event::register);
  }

  public static void onPlayerTickEvent(final TickEvent.PlayerTickEvent event)
  {
    if((event.phase != TickEvent.Phase.END) || (!event.player.level.isClientSide)) return;
    if((event.player.level.getGameTime() & 0x1) != 0) return;
    wile.redstonepen.detail.RcaSync.ClientRca.tick();
  }

  @OnlyIn(Dist.CLIENT)
  @Mod.EventBusSubscriber(Dist.CLIENT)
  public static class ForgeClientEvents
  {
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onRenderGui(net.minecraftforge.client.event.RenderGuiOverlayEvent.Post event)
    { Overlay.TextOverlayGui.INSTANCE.onRenderGui(event.getPoseStack()); }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("deprecation")
    public static void onRenderWorldOverlay(net.minecraftforge.client.event.RenderLevelStageEvent event)
    {
      if(event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER) {
        Overlay.TextOverlayGui.INSTANCE.onRenderWorldOverlay(event.getPoseStack(), event.getPartialTick());
      }
    }
  }

}
