/*
 * @file Overlay.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Renders status messages in one line.
 */
package wile.redstonepen.libmc.detail;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.BlockState;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.LightType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Optional;

public class Overlay
{
  public static void register()
  {
    if(SidedProxy.mc() != null) {
      MinecraftForge.EVENT_BUS.register(new TextOverlayGui());
      Networking.OverlayTextMessage.setHandler(TextOverlayGui::show);
    }
  }

  public static void show(PlayerEntity player, final ITextComponent message)
  { Networking.OverlayTextMessage.sendToPlayer(player, message, 3000); }

  public static void show(PlayerEntity player, final ITextComponent message, int delay)
  { Networking.OverlayTextMessage.sendToPlayer(player, message, delay); }

  public static void show(BlockState state, BlockPos pos)
  { show(state, pos, 100); }

  public static void show(BlockState state, BlockPos pos, int displayTimeoutMs)
  { DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ()->(()->TextOverlayGui.show(state, pos, displayTimeoutMs))); } // Only called when client side

  // -----------------------------------------------------------------------------
  // Client side handler
  // -----------------------------------------------------------------------------

  @Mod.EventBusSubscriber(Dist.CLIENT)
  @OnlyIn(Dist.CLIENT)
  public static class TextOverlayGui extends AbstractGui
  {
    private static final ITextComponent EMPTY_TEXT = new StringTextComponent("");
    private static final BlockState EMPTY_STATE = null;
    private static double overlay_y_ = 0.75;
    private static int text_color_ = 0x00ffaa00;
    private static int border_color_ = 0xaa333333;
    private static int background_color1_ = 0xaa333333;
    private static int background_color2_ = 0xaa444444;
    private final Minecraft mc;
    private static long text_deadline_ = 0;
    private static ITextComponent text_ = EMPTY_TEXT;
    private static long state_deadline_ = 0;
    private static @Nullable BlockState state_ = EMPTY_STATE;
    private static BlockPos pos_ = BlockPos.ZERO;
    private static int disable_dueto_exception = 16;

    public static void on_config(double overlay_y)
    { on_config(overlay_y, text_color_, border_color_, background_color1_, background_color2_); }

    public static void on_config(double overlay_y, int text_color, int border_color, int background_color1, int background_color2)
    {
      overlay_y_ = overlay_y;
      // currently const, just to circumvent "useless variable" warnings
      text_color_ = text_color;
      border_color_ = border_color;
      background_color1_ = background_color1;
      background_color2_ = background_color2;
    }

    public static synchronized ITextComponent text()
    { return text_; }

    public static synchronized long deadline()
    { return text_deadline_; }

    public static synchronized void hide()
    { text_deadline_ = 0; text_ = EMPTY_TEXT; }

    public static synchronized void show(ITextComponent s, int displayTimeoutMs)
    { text_ = (s==null)?(EMPTY_TEXT):(s.copy()); text_deadline_ = System.currentTimeMillis() + displayTimeoutMs; }

    public static synchronized void show(String s, int displayTimeoutMs)
    { text_ = ((s==null)||(s.isEmpty()))?(EMPTY_TEXT):(new StringTextComponent(s)); text_deadline_ = System.currentTimeMillis() + displayTimeoutMs; }

    public static synchronized void show(BlockState state, BlockPos pos, int displayTimeoutMs)
    { pos_ = new BlockPos(pos); state_ = state; state_deadline_ = System.currentTimeMillis() + displayTimeoutMs; }

    private static synchronized Optional<Tuple<BlockState,BlockPos>> state_pos()
    { return ((state_deadline_ < System.currentTimeMillis()) || (state_==EMPTY_STATE)) ? Optional.empty() : Optional.of(new Tuple<>(state_, pos_)); }

    TextOverlayGui()
    { super(); mc = SidedProxy.mc(); }

    @SubscribeEvent
    public void onRenderGui(RenderGameOverlayEvent.Post event)
    {
      if(disable_dueto_exception <= 0) return;
      if(event.getType() != RenderGameOverlayEvent.ElementType.CHAT) return;
      if(deadline() < System.currentTimeMillis()) return;
      if(text()==EMPTY_TEXT) return;
      String txt = text().getString();
      if(txt.isEmpty()) return;
      MatrixStack mxs = event.getMatrixStack();
      final MainWindow win = mc.getWindow();
      final FontRenderer fr = mc.font;
      final boolean was_unicode = fr.isBidirectional();
      try {
        final int cx = win.getGuiScaledWidth() / 2;
        final int cy = (int)(win.getGuiScaledHeight() * overlay_y_);
        final int w = fr.width(txt);
        final int h = fr.lineHeight;
        fillGradient(mxs, cx-(w/2)-3, cy-2, cx+(w/2)+2, cy+h+2, background_color1_, background_color2_);
        hLine(mxs, cx-(w/2)-3, cx+(w/2)+2, cy-2, border_color_);
        hLine(mxs, cx-(w/2)-3, cx+(w/2)+2, cy+h+2, border_color_);
        vLine(mxs, cx-(w/2)-3, cy-2, cy+h+2, border_color_);
        vLine(mxs, cx+(w/2)+2, cy-2, cy+h+2, border_color_);
        drawCenteredString(mxs, fr, text(), cx , cy+1, text_color_);
      } catch(Throwable ex) {
        if(--disable_dueto_exception <= 0) {
          Auxiliaries.logError("Disabled mod rendering due to repeated overlay-rendering exception:" + ex);
        }
      }
    }

    @SubscribeEvent
    public void onRenderWorldOverlay(RenderWorldLastEvent event)
    {
      if(disable_dueto_exception <= 0) return;
      final Optional<Tuple<BlockState,BlockPos>> sp = state_pos();
      if(!sp.isPresent()) return;
      final ClientWorld world = Minecraft.getInstance().level;
      final ClientPlayerEntity player = Minecraft.getInstance().player;
      if((player==null) || (world==null)) return;
      final BlockState state = sp.get().getA();
      final BlockPos pos = sp.get().getB();
      final MatrixStack mxs = event.getMatrixStack();
      mxs.pushPose();
      try {
        @SuppressWarnings("deprecation")
        final int light = (world.hasChunkAt(pos)) ? LightTexture.pack(world.getBrightness(LightType.BLOCK, pos), world.getBrightness(LightType.SKY, pos)) : LightTexture.pack(15, 15);
        final IRenderTypeBuffer buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        final double px = MathHelper.lerp(event.getPartialTicks(), player.xo, player.getX());
        final double py = MathHelper.lerp(event.getPartialTicks(), player.yo, player.getY());
        final double pz = MathHelper.lerp(event.getPartialTicks(), player.zo, player.getZ());
        mxs.translate((pos.getX()-px), (pos.getY()-py-player.getEyeHeight()), (pos.getZ()-pz));
        Minecraft.getInstance().getBlockRenderer().renderBlock(state, mxs, buffer, light, OverlayTexture.NO_OVERLAY, EmptyModelData.INSTANCE);
      } catch(Throwable ex) {
        if(--disable_dueto_exception <= 0) {
          Auxiliaries.logError("Disabled mod rendering due to repeated world-rendering exception:" + ex);
        }
      }
      mxs.popPose();
    }
  }

}
