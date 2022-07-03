/*
 * @file ModRedstonePenClient.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import wile.redstonepen.blocks.ControlBox;
import wile.redstonepen.blocks.RedstoneTrack;
import wile.redstonepen.detail.ModRenderers;
import wile.redstonepen.libmc.NetworkingClient;
import wile.redstonepen.libmc.Overlay;
import wile.redstonepen.libmc.Registries;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class ModRedstonePenClient implements ClientModInitializer
{
  public ModRedstonePenClient()
  {
    ModelLoadingRegistry.INSTANCE.registerModelProvider(ModRedstonePenClient::onRegisterModels);
  }

  @Override
  public void onInitializeClient()
  {
    NetworkingClient.clientInit(ModRedstonePen.MODID);
    Overlay.register();
    registerMenuGuis();
    registerBlockEntityRenderers();
    Overlay.TextOverlayGui.on_config(
            0.75,
            0x00ffaa00,
            0x55333333,
            0x55333333,
            0x55444444
    );
    WorldRenderEvents.AFTER_TRANSLUCENT.register((context)->Overlay.TextOverlayGui.INSTANCE.onRenderWorldOverlay(context.matrixStack(), context.tickDelta()));
    if(wile.redstonepen.detail.RcaSync.ClientRca.init()) {
      ClientTickEvents.END_CLIENT_TICK.register(ModRedstonePenClient::onPlayerTickEvent);
    }
  }

  // ----------------------------------------------------------------------------------------------------------------

  private static void onPlayerTickEvent(final net.minecraft.client.Minecraft mc)
  {
    if((mc.level==null) || (mc.level.getGameTime() & 0x1) != 0) return;
    wile.redstonepen.detail.RcaSync.ClientRca.tick();
  }

  @SuppressWarnings("unchecked")
  private static void registerBlockEntityRenderers()
  {
    net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
      (BlockEntityType<RedstoneTrack.TrackBlockEntity>) Registries.getBlockEntityTypeOfBlock("track"),
      wile.redstonepen.detail.ModRenderers.TrackTer::new
    );
  }

  @SuppressWarnings("unchecked")
  private static void registerMenuGuis()
  {
    MenuScreens.register((MenuType<ControlBox.ControlBoxUiContainer>)Registries.getMenuTypeOfBlock("control_box"), ControlBox.ControlBoxGui::new);
  }

  private static void onRegisterModels(ResourceManager manager, Consumer<ResourceLocation> out)
  {
    ModRenderers.TrackTer.registerModels().forEach(out);
  }

  private static void processContentClientSide()
  {
    BlockRenderLayerMap.INSTANCE.putBlock(ModContent.references.TRACK_BLOCK, RenderType.cutout());
  }

}
