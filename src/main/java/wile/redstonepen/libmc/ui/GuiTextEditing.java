/*
 * @file Guis.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Text Editing Widgets. Derived form Book-And-Quill.
 */
package wile.redstonepen.libmc.ui;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import wile.redstonepen.libmc.ui.Guis.Coord2d;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


@OnlyIn(Dist.CLIENT)
public class GuiTextEditing
{
  @OnlyIn(Dist.CLIENT)
  public static class MultiLineTextBox extends Guis.UiWidget implements GuiEventListener
  {
    private static final int NORM_LINE_HEIGHT = 9;
    private static final Consumer<MultiLineTextBox> ON_CHANGE_IGNORED = (tb)->{};
    private static final BiConsumer<MultiLineTextBox, Coord2d> ON_MOUSEMOVE_IGNORED = (xy,tb)->{};
    private int frame_tick_;
    private long last_clicked_ = 0;
    private int last_index_ = -1;
    private final TextFieldHelper edit_;
    private String text_ = "";
    private Font font_;
    private int line_height_ = NORM_LINE_HEIGHT;
    private float font_scale_ = 1f;
    private int max_text_size_ = 1024;
    private int font_color_ = 0xff000000;
    private int cursor_color_ = 0xff000000;
    private Consumer<MultiLineTextBox> on_changed_ = ON_CHANGE_IGNORED;
    private BiConsumer<MultiLineTextBox, Coord2d> on_mouse_move_ = ON_MOUSEMOVE_IGNORED;

    public MultiLineTextBox(int x, int y, int width, int height, Component title)
    {
      super(x, y, width, height, title);
      edit_ = new TextFieldHelper(
        this::getText, this::setText, this::getClipboard, this::setClipboard,
        (s)->s.length()<max_text_size_ && font_.wordWrapHeight(s, width*NORM_LINE_HEIGHT/line_height_)<=(height*NORM_LINE_HEIGHT/line_height_)
      );
    }

    public String getValue()
    { return eotTrimmed(text_); }

    public MultiLineTextBox setValue(String text)
    {
      if(text.length() > getMaxLength()) { text = text.substring(0, getMaxLength()); }
      if(!text_.equals(text)) {
        if(edit_ == null) {
          text_ = eotTrimmed(text);
        } else {
          boolean to_end = text_.isEmpty();
          int cur = edit_.getCursorPos();
          int sel = edit_.getSelectionPos();
          edit_.selectAll();
          edit_.insertText(eotTrimmed(text));
          if(to_end) sel = cur = text_.length();
          edit_.setSelectionPos(sel);
          edit_.setCursorPos(cur);
        }
        clearDisplayCache();
      }
      return this;
    }

    public int getMaxLength()
    { return max_text_size_; }

    public MultiLineTextBox setMaxLength(int size)
    {
      max_text_size_ = Mth.clamp(size, 2, 1024);
      if(text_.length() > max_text_size_) {
        text_ = eotTrimmed(text_.substring(0, max_text_size_));
        on_changed_.accept(this);
      }
      return this;
    }

    public Font getFont()
    { return font_; }

    public MultiLineTextBox setFont(Font fnt)
    { font_=fnt; return this; }

    public int getFontColor()
    { return font_color_; }

    public MultiLineTextBox setFontColor(int color)
    { font_color_ = color|0xff000000; return this; }

    public int getLineHeight()
    { return line_height_; }

    public MultiLineTextBox setLineHeight(int h)
    {
      line_height_ = Mth.clamp(h, 6, NORM_LINE_HEIGHT);
      font_scale_ = (((float)line_height_)/9f);
      return this;
    }

    public int getCursorColor()
    { return cursor_color_; }

    public MultiLineTextBox setCursorColor(int color)
    { cursor_color_=color|0xff000000; return this; }

    public MultiLineTextBox onValueChanged(Consumer<MultiLineTextBox> cb)
    { on_changed_ = cb; return this; }

    public MultiLineTextBox onMouseMove(BiConsumer<MultiLineTextBox, Coord2d> cb)
    { on_mouse_move_ = cb; return this; }

    public int getIndexUnderMouse(double mouseX, double mouseY)
    { return (font_ == null) ? 0 : (getDisplayCache().getIndexAtPosition(font_, screenCoordinates(Coord2d.of((int)mouseX, (int)mouseY), false))); }

    public Coord2d getCoordinatesAtIndex(int textIndex)
    {
      if(font_ == null) return Coord2d.ORIGIN;
      textIndex = Mth.clamp(textIndex, 0, getDisplayCache().fullText.length());
      final int lindex = findLineFromPos(getDisplayCache().lineStarts, textIndex);
      if(lindex < 0 || lindex >= getDisplayCache().lineStarts.length) return Coord2d.ORIGIN;
      final LineInfo li = getDisplayCache().lines[lindex];
      textIndex = textIndex - getDisplayCache().lineStarts[lindex];
      textIndex = Mth.clamp(textIndex, 0, li.contents.length());
      final int ox = (int)font_.getSplitter().stringWidth(li.contents.substring(0, textIndex));
      final int oy = getDisplayCache().lines[0].y;
      return Coord2d.of(li.x+(ox*line_height_/NORM_LINE_HEIGHT), oy + ((li.y-oy)*line_height_/NORM_LINE_HEIGHT));
    }

    public String getWordAtPosition(Coord2d xy)
    { return ""; } // implement

    //---------------------------------------------------------------------------------

    @Override
    public MultiLineTextBox init(Screen parent)
    { return init(parent, Coord2d.of(x,y)); }

    @Override
    public MultiLineTextBox init(Screen parent, Coord2d position)
    {
      super.init(parent, position);
      font_ = parent.getMinecraft().font;
      font_color_ = 0xff000000;
      cursor_color_ = 0xff000000;
      clearDisplayCache();
      return this;
    }

    @Override
    protected void onFocusedChanged(boolean focus)
    {}

    @Override
    public boolean mouseClicked(double x, double y, int button)
    {
      if((!active) || (!visible) || (x<this.x) || (y<this.y) || (x>this.x+this.width) || (y>this.y+this.height)) return false;
      if(button != 0) return true;
      final Coord2d sc = screenCoordinates(Coord2d.of((int)x, (int)y), false);
      final int index = getDisplayCache().getIndexAtPosition(font_, Coord2d.of(sc.x*NORM_LINE_HEIGHT/line_height_, sc.y*NORM_LINE_HEIGHT/line_height_));
      if(index >= 0) {
        if((index==last_index_) && ((Util.getMillis()-last_clicked_)<250)) {
          if(edit_.isSelecting()) {
            edit_.selectAll();
          } else {
            // Select word
            edit_.setSelectionRange(StringSplitter.getWordPosition(getText(), -1, index, false), StringSplitter.getWordPosition(getText(), 1, index, false));
          }
        } else {
          edit_.setCursorPos(index, Screen.hasShiftDown());
        }
        clearDisplayCache();
      }
      last_index_ = index;
      last_clicked_ = index;
      if(isFocused()) setFocused(true);
      return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy)
    {
      if(super.mouseDragged(x, y, button, dx, dy) || (button != 0)) return true;
      if((!active) || (!visible)) return false;
      final Coord2d sc = screenCoordinates(Coord2d.of((int)x, (int)y), false);
      edit_.setCursorPos(getDisplayCache().getIndexAtPosition(font_, Coord2d.of(sc.x*NORM_LINE_HEIGHT/line_height_, sc.y*NORM_LINE_HEIGHT/line_height_)), true);
      clearDisplayCache();
      return true;
    }

    @Override
    public boolean charTyped(char key, int code)
    {
      if(super.charTyped(key, code)) return true;
      if((!active) || (!visible)) return false;
      if(!SharedConstants.isAllowedChatCharacter(key)) return false;
      edit_.insertText(Character.toString(key));
      clearDisplayCache();
      on_changed_.accept(this);
      return true;
    }

    @Override
    public boolean keyPressed(int key, int x, int y)
    {
      if(super.keyPressed(key, x, y)) return true;
      if((!active) || (!visible)) return false;
      String text_before = text_;
      if(!specialKeyMatched(key)) return isFocused();
      clearDisplayCache();
      if((key == 257/*enter*/) && !edit_.isSelecting() && edit_.getCursorPos()<text_.length()-2) {
        final int cp = edit_.getCursorPos();
        edit_.selectAll();
        edit_.insertText(eotTrimmed(text_));
        edit_.setSelectionPos(cp);
        edit_.setCursorPos(cp, false);
      }
      if(!text_before.equals(text_)) on_changed_.accept(this);
      return true;
    }

    @Override
    public void renderButton(PoseStack mxs, int mouseX, int mouseY, float partialTicks)
    {
      if(!this.visible) return;
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
      mxs.pushPose();
      int ox = (int)(this.x * (1.-font_scale_));
      int oy = (int)(this.y * (1.-font_scale_));
      mxs.translate(ox, oy, 0);
      mxs.scale(font_scale_, font_scale_, font_scale_);

      final DisplayCache cache = getDisplayCache();
      for(LineInfo li:cache.lines) font_.draw(mxs, li.asComponent, li.x, li.y, font_color_);
      this.renderHighlight(cache.selection);
      this.renderCursor(mxs, cache.cursor, cache.cursorAtEnd);
      {
        Coord2d xy = getMousePosition();
        if((xy.x>=0) && (xy.y>=0) && (xy.x<width) && (xy.y<height)) on_mouse_move_.accept(this, getMousePosition());
      }
      mxs.popPose();
    }

    //---------------------------------------------------------------------------------

    private static String eotTrimmed(String text)
    { return text.replaceAll("\\s+$", "\n"); }

    private String getText()
    { return text_; }

    private void setText(String text)
    { text_ = text; clearDisplayCache(); }

    private void setClipboard(String text)
    { if(Minecraft.getInstance()!=null) TextFieldHelper.setClipboardContents(Minecraft.getInstance(), eotTrimmed(text)); }

    private String getClipboard()
    { return (Minecraft.getInstance()!=null) ? eotTrimmed(TextFieldHelper.getClipboardContents(Minecraft.getInstance())) : (""); }

    private boolean specialKeyMatched(int key)
    {
      if(Screen.isSelectAll(key)) { edit_.selectAll(); return true; }
      if(Screen.isCopy(key)) { edit_.copy(); return true; }
      if(Screen.isPaste(key)) { edit_.paste(); return true; }
      if(Screen.isCut(key)) { edit_.cut(); return true; }
      switch(key) {
        case 257, 335 -> { edit_.insertText("\n"); return true; }
        case 259 -> { edit_.removeCharsFromCursor(-1); return true; }
        case 261 -> { edit_.removeCharsFromCursor(1); return true; }
        case 262 -> { edit_.moveByChars(1, Screen.hasShiftDown()); return true; }
        case 263 -> { edit_.moveByChars(-1, Screen.hasShiftDown()); return true; }
        case 264 -> { changeLine(1); return true; } // arrow down
        case 265 -> { changeLine(-1); return true; } // arrow up
        case 266 -> { edit_.setCursorPos(0, Screen.hasShiftDown());  return true; } // page up
        case 267 -> { edit_.setCursorPos(text_.length(), Screen.hasShiftDown()); return true; } // page down
        case 268 -> { edit_.setCursorPos(getDisplayCache().findLineStart(edit_.getCursorPos()), Screen.hasShiftDown()); return true; } // home hey
        case 269 -> { edit_.setCursorPos(getDisplayCache().findLineEnd(edit_.getCursorPos()), Screen.hasShiftDown()); return true; } // end key
        default ->  { return false; }
      }
    }

    private void changeLine(int incr)
    { edit_.setCursorPos(getDisplayCache().changeLine(edit_.getCursorPos(), incr), Screen.hasShiftDown()); }

    private void renderCursor(PoseStack mxs, Coord2d pos, boolean at_end)
    {
      if(!active || !visible) frame_tick_ = 0;
      if((++frame_tick_ & 0x3f) < 0x20) return;
      pos = screenCoordinates(pos, true);
      if(!at_end) {
        GuiComponent.fill(mxs, pos.x, pos.y - 1, pos.x + 1, pos.y + NORM_LINE_HEIGHT, cursor_color_);
      } else {
        font_.draw(mxs, "_", (float)pos.x, (float)pos.y, 0);
      }
    }

    private void renderHighlight(Rect2i[] line_rects)
    {
      RenderSystem.setShader(GameRenderer::getPositionShader);
      RenderSystem.setShaderColor(0.0F, 0.0F, 0, 255f);
      RenderSystem.disableTexture();
      RenderSystem.enableColorLogicOp();
      RenderSystem.logicOp(GlStateManager.LogicOp.OR_REVERSE);
      final BufferBuilder buf = Tesselator.getInstance().getBuilder();
      buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
      final double ox = this.x * (1.-font_scale_);
      final double oy = this.y * (1.-font_scale_);
      for(Rect2i rc: line_rects) {
        final int x0 = (int)Math.floor(ox+(double)(rc.getX())*font_scale_)-1;
        final int y0 = (int)Math.floor(oy+(double)(rc.getY())*font_scale_)-1;
        final int x1 = (int)Math.ceil(x0+(double)(rc.getWidth())*font_scale_);
        final int y1 = (int)Math.ceil(y0+(double)(rc.getHeight())*font_scale_)-1;
        buf.vertex(x0, y1, 0.0D).endVertex();
        buf.vertex(x1, y1, 0.0D).endVertex();
        buf.vertex(x1, y0, 0.0D).endVertex();
        buf.vertex(x0, y0, 0.0D).endVertex();
      }
      Tesselator.getInstance().end();
      RenderSystem.disableColorLogicOp();
      RenderSystem.enableTexture();
    }

    @Nullable
    private DisplayCache display_cache_ = DisplayCache.EMPTY;

    private DisplayCache getDisplayCache()
    { if(display_cache_==null) { display_cache_=rebuildDisplayCache(); } return display_cache_; }

    private void clearDisplayCache()
    { this.display_cache_ = null; }

    private DisplayCache rebuildDisplayCache()
    {
      final String full_text = getText();
      if(full_text.isEmpty()) return DisplayCache.EMPTY;
      final int cur_pos = edit_.getCursorPos();
      final int sel_pos = edit_.getSelectionPos();
      final IntList lsp = new IntArrayList();
      final List<LineInfo> line_infos = Lists.newArrayList();
      final MutableInt line_no = new MutableInt();
      final MutableBoolean line_terminated = new MutableBoolean();
      final StringSplitter ssp = font_.getSplitter();
      ssp.splitLines(full_text, this.width*NORM_LINE_HEIGHT/line_height_, Style.EMPTY, true, (text, spos, epos) -> {
        final String full_line = full_text.substring(spos, epos);
        line_terminated.setValue(full_line.endsWith("\n"));
        final String line = StringUtils.stripEnd(full_line, " \n");
        lsp.add(spos);
        final Coord2d pxy = screenCoordinates(new Coord2d(0, line_no.getAndIncrement() * NORM_LINE_HEIGHT), true);
        line_infos.add(new LineInfo(text, line, pxy.x, pxy.y));
      });
      final int[] line_starts = lsp.toIntArray();
      final boolean cur_at_eos = (cur_pos==full_text.length());
      Coord2d ppos;
      if(cur_at_eos && line_terminated.isTrue()) {
        ppos = new Coord2d(0, line_infos.size() * NORM_LINE_HEIGHT);
      } else {
        final int lno = findLineFromPos(line_starts, cur_pos);
        final int lpx = font_.width(full_text.substring(line_starts[lno], cur_pos));
        ppos = new Coord2d(lpx, lno * NORM_LINE_HEIGHT);
      }
      List<Rect2i> selection_blocks = Lists.newArrayList();
      if(cur_pos != sel_pos) {
        int l2 = Math.min(cur_pos, sel_pos);
        int i1 = Math.max(cur_pos, sel_pos);
        int j1 = findLineFromPos(line_starts, l2);
        int k1 = findLineFromPos(line_starts, i1);
        if(j1 == k1) {
          int l1 = j1 * NORM_LINE_HEIGHT;
          int i2 = line_starts[j1];
          selection_blocks.add(createPartialLineSelection(full_text, ssp, l2, i1, l1, i2));
        } else {
          int i3 = j1 + 1 > line_starts.length ? full_text.length() : line_starts[j1 + 1];
          selection_blocks.add(createPartialLineSelection(full_text, ssp, l2, i3, j1 * NORM_LINE_HEIGHT, line_starts[j1]));
          for(int j3 = j1 + 1; j3 < k1; ++j3) {
            int j2 = j3 * NORM_LINE_HEIGHT;
            String s1 = full_text.substring(line_starts[j3], line_starts[j3 + 1]).replaceAll("[\\r\\n]+$", "");
            int k2 = (int)ssp.stringWidth(s1);
            selection_blocks.add(createSelection(new Coord2d(0, j2), new Coord2d(k2, j2 + NORM_LINE_HEIGHT)));
          }
          selection_blocks.add(createPartialLineSelection(full_text, ssp, line_starts[k1], i1, k1 * NORM_LINE_HEIGHT, line_starts[k1]));
        }
      }
      return new DisplayCache(full_text, ppos, cur_at_eos, line_starts, line_infos.toArray(new LineInfo[0]), selection_blocks.toArray(new Rect2i[0]));
    }

    private static int findLineFromPos(int[] line_starts, int cursor_pos)
    { final int i = Arrays.binarySearch(line_starts, cursor_pos); return (i<0) ? (-(i+2)) : (i); }

    private Rect2i createPartialLineSelection(String text, StringSplitter ssp, int spos, int epos, int liney, int line_start_pos)
    {
      final String s0 = text.substring(line_start_pos, spos);
      final String s1 = text.substring(line_start_pos, epos).replaceAll("[\\r\\n]+$", "");
      return createSelection(new Coord2d((int)ssp.stringWidth(s0), liney), new Coord2d((int)ssp.stringWidth(s1), liney + NORM_LINE_HEIGHT));
    }

    private Rect2i createSelection(Coord2d pos1, Coord2d pos2)
    {
      final Coord2d cd1 = screenCoordinates(pos1, true);
      final Coord2d cd2 = screenCoordinates(pos2, true);
      final int x0 = Math.min(cd1.x, cd2.x);
      final int x1 = Math.max(cd1.x, cd2.x);
      final int y0 = Math.min(cd1.y, cd2.y);
      final int y1 = Math.max(cd1.y, cd2.y);
      return new Rect2i(x0, y0, x1-x0, y1-y0);
    }

    @OnlyIn(Dist.CLIENT)
    static class DisplayCache
    {
      static final DisplayCache EMPTY = new DisplayCache("", new Coord2d(0, 0), true, new int[]{0}, new LineInfo[]{new LineInfo(Style.EMPTY, "", 0, 0)}, new Rect2i[0]);
      private final String fullText;
      final Coord2d cursor;
      final boolean cursorAtEnd;
      private final int[] lineStarts;
      final LineInfo[] lines;
      final Rect2i[] selection;

      public DisplayCache(String text, Coord2d cur, boolean at_end, int[] line_starts, LineInfo[] line_data, Rect2i[] sel)
      { fullText = text; cursor = cur; cursorAtEnd = at_end; lineStarts = line_starts; lines = line_data; selection = sel; }

      public int getIndexAtPosition(Font font, Coord2d pos)
      {
        int i = pos.y / NORM_LINE_HEIGHT;
        if(i < 0) return 0;
        if(i >= lines.length) return this.fullText.length();
        return this.lineStarts[i] + font.getSplitter().plainIndexAtWidth(lines[i].contents, pos.x, lines[i].style);
      }

      public int changeLine(int cursor_pos, int length)
      {
        int i = findLineFromPos(this.lineStarts, cursor_pos);
        int j = i + length;
        int k;
        if (0 <= j && j < this.lineStarts.length) {
          int l = cursor_pos - this.lineStarts[i];
          int i1 = this.lines[j].contents.length();
          k = this.lineStarts[j] + Math.min(l, i1);
        } else {
          k = cursor_pos;
        }
        return k;
      }

      public int findLineStart(int cursor_pos)
      {
        int i = findLineFromPos(this.lineStarts, cursor_pos);
        return this.lineStarts[i];
      }

      public int findLineEnd(int cursor_pos)
      {
        int i = findLineFromPos(this.lineStarts, cursor_pos);
        return this.lineStarts[i] + this.lines[i].contents.length();
      }
    }

    @OnlyIn(Dist.CLIENT)
    static class LineInfo
    {
      final Style style;
      final String contents;
      final Component asComponent;
      final int x;
      final int y;

      public LineInfo(Style fs, String s, int x0, int y0)
      { style = fs; contents = s; x = x0; y = y0; asComponent = (new TextComponent(contents)).setStyle(style); }
    }

  }

}
