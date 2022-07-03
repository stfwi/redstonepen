package wile.redstonepen.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
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
  @Inject(at=@At("TAIL"), method="render(Lcom/mojang/blaze3d/vertex/PoseStack;F)V")
  private void render(com.mojang.blaze3d.vertex.PoseStack mxs, float partialTicks, CallbackInfo info)
  {
    if(Overlay.TextOverlayGui.deadline() < System.currentTimeMillis()) return;
    if(Overlay.TextOverlayGui.text() == Overlay.TextOverlayGui.EMPTY_TEXT) return;
    mxs.pushPose();
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    Overlay.TextOverlayGui.INSTANCE.onRenderGui(mxs);
    RenderSystem.disableBlend();
    mxs.popPose();
    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
  }
}
