package wile.redstonepen.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wile.redstonepen.libmc.Overlay;

@Environment(EnvType.CLIENT)
@Mixin(net.minecraft.client.gui.Gui.class)
public class GuiRenderingMixin
{
  @Inject(at=@At("TAIL"), method="render(Lnet/minecraft/client/gui/GuiGraphics;F)V")
  private void render(net.minecraft.client.gui.GuiGraphics gg, float partialTicks, CallbackInfo info)
  {
    if(Overlay.TextOverlayGui.deadline() < System.currentTimeMillis()) return;
    if(Overlay.TextOverlayGui.text() == Overlay.TextOverlayGui.EMPTY_TEXT) return;
    //    com.mojang.blaze3d.vertex.PoseStack mxs = gg.pose();
    //    mxs.pushPose();
    //    RenderSystem.enableBlend();
    //    RenderSystem.defaultBlendFunc();
    Overlay.TextOverlayGui.INSTANCE.onRenderGui(gg);
    //    RenderSystem.disableBlend();
    //    mxs.popPose();
    //    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
  }
}
