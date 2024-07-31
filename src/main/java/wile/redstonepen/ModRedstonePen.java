/*
 * @file ModRedstonePen.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import wile.redstonepen.blocks.ControlBox;
import wile.redstonepen.blocks.RedstoneTrack;
import wile.redstonepen.libmc.Auxiliaries;
import wile.redstonepen.libmc.Networking;
import wile.redstonepen.libmc.Overlay;
import wile.redstonepen.libmc.Registries;

@Mod("redstonepen")
public class ModRedstonePen
{
  public ModRedstonePen(IEventBus bus)
  {
    Auxiliaries.init();
    Auxiliaries.logGitVersion();
    Registries.init();
    ModContent.init();
    bus.addListener(LiveCycleEvents::onConstruct);
    bus.addListener(LiveCycleEvents::onRegister);
    bus.addListener(LiveCycleEvents::onRegisterNetwork);
    CREATIVE_MODE_TABS.register(bus);
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Createive Mode Tab
  // -------------------------------------------------------------------------------------------------------------------

  public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, ModConstants.MODID);
  public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register(
    "tab_"+ModConstants.MODID, ()->CreativeModeTab.builder()
      .title(Component.translatable("itemGroup.tabredstonepen"))
      .withTabsBefore(CreativeModeTabs.COMBAT)
      .icon(() -> new ItemStack(Registries.getItem("pen")))
      .displayItems((parameters, output) -> Registries.getRegisteredItems().forEach(
        it->{ if(!(it instanceof BlockItem bit) || (bit.getBlock() != ModContent.references.TRACK_BLOCK)) output.accept(it); })
      ).build()
  );

  // -------------------------------------------------------------------------------------------------------------------
  // Events
  // -------------------------------------------------------------------------------------------------------------------

  private static class LiveCycleEvents
  {
    private static void onConstruct(final FMLConstructModEvent event)
    {
      wile.redstonepen.detail.RcaSync.CommonRca.init();
    }

    private static void onRegister(RegisterEvent event)
    {
      final String registry_name = event.getRegistry().key().location().toString();
      if(!registry_name.equals("minecraft:block")) return;
      Registries.instantiateAll();
      ModContent.initReferences();
    }

    private static void onRegisterNetwork(final RegisterPayloadHandlersEvent event)
    {
      PayloadRegistrar registrar = event.registrar("v1");
      wile.redstonepen.libmc.Networking.init(registrar);
      wile.redstonepen.libmc.NetworkingClient.clientInit(registrar);
    }

    private static void onLoadComplete(final FMLLoadCompleteEvent event)
    {
    }
  }

  @EventBusSubscriber(modid=ModConstants.MODID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
  public static class ClientEvents
  {
    @SubscribeEvent
    @SuppressWarnings({"unchecked"})
    public static void onClientSetup(final FMLClientSetupEvent event)
    {
      Networking.OverlayTextMessage.setHandler(Overlay.TextOverlayGui::show);
      Overlay.on_config(0.75, 0x00ffaa00, 0x55333333, 0x55333333, 0x55444444);
      BlockEntityRenderers.register((BlockEntityType<RedstoneTrack.TrackBlockEntity>)Registries.getBlockEntityTypeOfBlock("track"), wile.redstonepen.detail.ModRenderers.TrackTer::new);
      // Player client tick if RCA existing.
      if(wile.redstonepen.detail.RcaSync.ClientRca.init()) {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (final PlayerTickEvent.Post ev)->wile.redstonepen.detail.RcaSync.ClientRca.tick());
      }
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked"})
    public static void onRegisterMenuScreens(final RegisterMenuScreensEvent event)
    {
      event.register((MenuType<ControlBox.ControlBoxUiContainer>)Registries.getMenuTypeOfBlock("control_box"), ControlBox.ControlBoxGui::new);
    }

    @SubscribeEvent
    public static void onRegisterModels(final ModelEvent.RegisterAdditional event)
    {
      wile.redstonepen.detail.ModRenderers.TrackTer.registerModels().forEach(event::register);
    }
  }

  @EventBusSubscriber(modid=ModConstants.MODID, value=Dist.CLIENT)
  public static class ClientGameEvents
  {
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onRenderGui(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event)
    {
      Overlay.TextOverlayGui.INSTANCE.onRenderGui(event.getGuiGraphics());
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onRenderWorldOverlay(net.neoforged.neoforge.client.event.RenderLevelStageEvent event)
    {
      if(event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS) {
        Overlay.TextOverlayGui.INSTANCE.onRenderWorldOverlay(event.getPoseStack(), event.getRenderTick());
      }
    }
  }

}
