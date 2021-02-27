/*
 * @file Auxiliaries.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * General commonly used functionality.
 */
package wile.redstonepen.libmc.detail;

import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Auxiliaries
{
  private static String modid;
  private static Logger logger;
  private static Supplier<CompoundNBT> server_config_supplier = ()->new CompoundNBT();

  public static void init(String modid, Logger logger, Supplier<CompoundNBT> server_config_supplier)
  {
    Auxiliaries.modid = modid;
    Auxiliaries.logger = logger;
    Auxiliaries.server_config_supplier = server_config_supplier;
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Mod specific exports
  // -------------------------------------------------------------------------------------------------------------------

  public static String modid()
  { return modid; }

  public static Logger logger()
  { return logger; }

  // -------------------------------------------------------------------------------------------------------------------
  // Sideness, system/environment, tagging interfaces
  // -------------------------------------------------------------------------------------------------------------------

  public interface IExperimentalFeature {}

  public static final boolean isModLoaded(final String registry_name)
  { return ModList.get().isLoaded(registry_name); }

  public static final boolean isDevelopmentMode()
  { return SharedConstants.developmentMode; }

  @OnlyIn(Dist.CLIENT)
  public static final boolean isShiftDown()
  {
    return (InputMappings.isKeyDown(SidedProxy.mc().getMainWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) ||
            InputMappings.isKeyDown(SidedProxy.mc().getMainWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT));
  }

  @OnlyIn(Dist.CLIENT)
  public static final boolean isCtrlDown()
  {
    return (InputMappings.isKeyDown(SidedProxy.mc().getMainWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) ||
            InputMappings.isKeyDown(SidedProxy.mc().getMainWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL));
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Logging
  // -------------------------------------------------------------------------------------------------------------------

  public static final void logInfo(final String msg)
  { logger.info(msg); }

  public static final void logWarn(final String msg)
  { logger.warn(msg); }

  public static final void logError(final String msg)
  { logger.error(msg); }

  // -------------------------------------------------------------------------------------------------------------------
  // Localization, text formatting
  // -------------------------------------------------------------------------------------------------------------------

  /**
   * Text localisation wrapper, implicitly prepends `MODID` to the
   * translation keys. Forces formatting argument, nullable if no special formatting shall be applied..
   */
  public static TranslationTextComponent localizable(String modtrkey, Object... args)
  {
    return new TranslationTextComponent((modtrkey.startsWith("block.") || (modtrkey.startsWith("item."))) ? (modtrkey) : (modid+"."+modtrkey), args);
  }

  public static TranslationTextComponent localizable(String modtrkey)
  { return localizable(modtrkey, new Object[]{}); }

  public static TranslationTextComponent localizable_block_key(String blocksubkey)
  { return new TranslationTextComponent("block."+modid+"."+blocksubkey); }

  @OnlyIn(Dist.CLIENT)
  public static String localize(String translationKey, Object... args)
  {
    TranslationTextComponent tr = new TranslationTextComponent(translationKey, args);
    tr.mergeStyle(TextFormatting.RESET);
    final String ft = tr.getString();
    if(ft.contains("${")) {
      // Non-recursive, non-argument lang file entry cross referencing.
      Pattern pt = Pattern.compile("\\$\\{([^}]+)\\}");
      Matcher mt = pt.matcher(ft);
      StringBuffer sb = new StringBuffer();
      while(mt.find()) {
        String m = mt.group(1);
        if(m.contains("?")) {
          String[] kv = m.split("\\?", 2);
          String key = kv[0].trim();
          boolean not = key.startsWith("!");
          if(not) key = key.replaceFirst("!", "");
          m = kv[1].trim();
          if(!server_config_supplier.get().contains(key)) {
            m = "";
          } else {
            boolean r = server_config_supplier.get().getBoolean(key);
            if(not) r = !r;
            if(!r) m = "";
          }
        }
        mt.appendReplacement(sb, Matcher.quoteReplacement((new TranslationTextComponent(m)).getString().trim()));
      }
      mt.appendTail(sb);
      return sb.toString();
    } else {
      return ft;
    }
  }

  /**
   * Returns true if a given key is translated for the current language.
   */
  @OnlyIn(Dist.CLIENT)
  public static boolean hasTranslation(String key)
  { return net.minecraft.client.resources.I18n.hasKey(key); }

  public static final class Tooltip
  {
    @OnlyIn(Dist.CLIENT)
    public static boolean extendedTipCondition()
    { return isShiftDown(); }

    @OnlyIn(Dist.CLIENT)
    public static boolean helpCondition()
    { return isShiftDown() && isCtrlDown(); }

    /**
     * Adds an extended tooltip or help tooltip depending on the key states of CTRL and SHIFT.
     * Returns true if the localisable help/tip was added, false if not (either not CTL/SHIFT or
     * no translation found).
     */
    @OnlyIn(Dist.CLIENT)
    public static boolean addInformation(@Nullable String advancedTooltipTranslationKey, @Nullable String helpTranslationKey, List<ITextComponent> tooltip, ITooltipFlag flag, boolean addAdvancedTooltipHints)
    {
      // Note: intentionally not using keybinding here, this must be `control` or `shift`.
      final boolean help_available = (helpTranslationKey != null) && Auxiliaries.hasTranslation(helpTranslationKey + ".help");
      final boolean tip_available = (advancedTooltipTranslationKey != null) && Auxiliaries.hasTranslation(helpTranslationKey + ".tip");
      if((!help_available) && (!tip_available)) return false;
      String tip_text = "";
      if(helpCondition()) {
        if(help_available) tip_text = localize(helpTranslationKey + ".help");
      } else if(extendedTipCondition()) {
        if(tip_available) tip_text = localize(advancedTooltipTranslationKey + ".tip");
      } else if(addAdvancedTooltipHints) {
        if(tip_available) tip_text += localize(modid + ".tooltip.hint.extended") + (help_available ? " " : "");
        if(help_available) tip_text += localize(modid + ".tooltip.hint.help");
      }
      if(tip_text.isEmpty()) return false;
      String[] tip_list = tip_text.split("\\r?\\n");
      for(String tip:tip_list) {
        tooltip.add(new StringTextComponent(tip.replaceAll("\\s+$","").replaceAll("^\\s+", "")).mergeStyle(TextFormatting.GRAY));
      }
      return true;
    }

    /**
     * Adds an extended tooltip or help tooltip for a given stack depending on the key states of CTRL and SHIFT.
     * Format in the lang file is (e.g. for items): "item.MODID.REGISTRYNAME.tip" and "item.MODID.REGISTRYNAME.help".
     * Return value see method pattern above.
     */
    @OnlyIn(Dist.CLIENT)
    public static boolean addInformation(ItemStack stack, @Nullable IBlockReader world, List<ITextComponent> tooltip, ITooltipFlag flag, boolean addAdvancedTooltipHints)
    { return addInformation(stack.getTranslationKey(), stack.getTranslationKey(), tooltip, flag, addAdvancedTooltipHints); }

    @OnlyIn(Dist.CLIENT)
    public static boolean addInformation(String translation_key, List<ITextComponent> tooltip)
    {
      if(!Auxiliaries.hasTranslation(translation_key)) return false;
      tooltip.add(new StringTextComponent(localize(translation_key).replaceAll("\\s+$","").replaceAll("^\\s+", "")).mergeStyle(TextFormatting.GRAY));
      return true;
    }

  }

  @SuppressWarnings("unused")
  public static void playerChatMessage(final PlayerEntity player, final String message)
  {
    String s = message.trim();
    if(!s.isEmpty()) player.sendMessage(new TranslationTextComponent(s), new UUID(0,0));
  }

  public static @Nullable ITextComponent unserializeTextComponent(String serialized)
  { return ITextComponent.Serializer.getComponentFromJson(serialized); }

  public static String serializeTextComponent(ITextComponent tc)
  { return (tc==null) ? ("") : (ITextComponent.Serializer.toJson(tc)); }

  // -------------------------------------------------------------------------------------------------------------------
  // Item NBT data
  // -------------------------------------------------------------------------------------------------------------------

  /**
   * Equivalent to getDisplayName(), returns null if no custom name is set.
   */
  public static @Nullable ITextComponent getItemLabel(ItemStack stack)
  {
    CompoundNBT nbt = stack.getChildTag("display");
    if(nbt != null && nbt.contains("Name", 8)) {
      try {
        ITextComponent tc = unserializeTextComponent(nbt.getString("Name"));
        if(tc != null) return tc;
        nbt.remove("Name");
      } catch(Exception e) {
        nbt.remove("Name");
      }
    }
    return null;
  }

  public static ItemStack setItemLabel(ItemStack stack, @Nullable ITextComponent name)
  {
    if(name != null) {
      CompoundNBT nbt = stack.getOrCreateChildTag("display");
      nbt.putString("Name", serializeTextComponent(name));
    } else {
      if(stack.hasTag()) stack.removeChildTag("display");
    }
    return stack;
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Block handling
  // -------------------------------------------------------------------------------------------------------------------

  public static final AxisAlignedBB getPixeledAABB(double x0, double y0, double z0, double x1, double y1, double z1)
  { return new AxisAlignedBB(x0/16.0, y0/16.0, z0/16.0, x1/16.0, y1/16.0, z1/16.0); }

  public static final AxisAlignedBB getRotatedAABB(AxisAlignedBB bb, Direction new_facing, boolean horizontal_rotation)
  {
    if(!horizontal_rotation) {
      switch(new_facing.getIndex()) {
        case 0: return new AxisAlignedBB(1-bb.maxX,   bb.minZ,   bb.minY, 1-bb.minX,   bb.maxZ,   bb.maxY); // D
        case 1: return new AxisAlignedBB(1-bb.maxX, 1-bb.maxZ, 1-bb.maxY, 1-bb.minX, 1-bb.minZ, 1-bb.minY); // U
        case 2: return new AxisAlignedBB(  bb.minX,   bb.minY,   bb.minZ,   bb.maxX,   bb.maxY,   bb.maxZ); // N --> bb
        case 3: return new AxisAlignedBB(1-bb.maxX,   bb.minY, 1-bb.maxZ, 1-bb.minX,   bb.maxY, 1-bb.minZ); // S
        case 4: return new AxisAlignedBB(  bb.minZ,   bb.minY, 1-bb.maxX,   bb.maxZ,   bb.maxY, 1-bb.minX); // W
        case 5: return new AxisAlignedBB(1-bb.maxZ,   bb.minY,   bb.minX, 1-bb.minZ,   bb.maxY,   bb.maxX); // E
      }
    } else {
      switch(new_facing.getIndex()) {
        case 0: return new AxisAlignedBB(  bb.minX, bb.minY,   bb.minZ,   bb.maxX, bb.maxY,   bb.maxZ); // D --> bb
        case 1: return new AxisAlignedBB(  bb.minX, bb.minY,   bb.minZ,   bb.maxX, bb.maxY,   bb.maxZ); // U --> bb
        case 2: return new AxisAlignedBB(  bb.minX, bb.minY,   bb.minZ,   bb.maxX, bb.maxY,   bb.maxZ); // N --> bb
        case 3: return new AxisAlignedBB(1-bb.maxX, bb.minY, 1-bb.maxZ, 1-bb.minX, bb.maxY, 1-bb.minZ); // S
        case 4: return new AxisAlignedBB(  bb.minZ, bb.minY, 1-bb.maxX,   bb.maxZ, bb.maxY, 1-bb.minX); // W
        case 5: return new AxisAlignedBB(1-bb.maxZ, bb.minY,   bb.minX, 1-bb.minZ, bb.maxY,   bb.maxX); // E
      }
    }
    return bb;
  }

  public static final AxisAlignedBB[] getRotatedAABB(AxisAlignedBB[] bbs, Direction new_facing, boolean horizontal_rotation)
  {
    final AxisAlignedBB[] transformed = new AxisAlignedBB[bbs.length];
    for(int i=0; i<bbs.length; ++i) transformed[i] = getRotatedAABB(bbs[i], new_facing, horizontal_rotation);
    return transformed;
  }

  public static final AxisAlignedBB getYRotatedAABB(AxisAlignedBB bb, int clockwise_90deg_steps)
  {
    final Direction direction_map[] = new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    return getRotatedAABB(bb, direction_map[(clockwise_90deg_steps+4096) & 0x03], true);
  }

  public static final AxisAlignedBB[] getYRotatedAABB(AxisAlignedBB[] bbs, int clockwise_90deg_steps)
  {
    final AxisAlignedBB[] transformed = new AxisAlignedBB[bbs.length];
    for(int i=0; i<bbs.length; ++i) transformed[i] = getYRotatedAABB(bbs[i], clockwise_90deg_steps);
    return transformed;
  }

  public static final AxisAlignedBB getMirroredAABB(AxisAlignedBB bb, Axis axis)
  {
    switch(axis) {
      case X: return new AxisAlignedBB(1-bb.maxX, bb.minY, bb.minZ, 1-bb.minX, bb.maxY, bb.maxZ);
      case Y: return new AxisAlignedBB(bb.minX, 1-bb.maxY, bb.minZ, bb.maxX, 1-bb.minY, bb.maxZ);
      case Z: return new AxisAlignedBB(bb.minX, bb.minY, 1-bb.maxZ, bb.maxX, bb.maxY, 1-bb.minZ);
      default: return bb;
    }
  }

  public static final AxisAlignedBB[] getMirroredAABB(AxisAlignedBB[] bbs, Axis axis)
  {
    final AxisAlignedBB[] transformed = new AxisAlignedBB[bbs.length];
    for(int i=0; i<bbs.length; ++i) transformed[i] = getMirroredAABB(bbs[i], axis);
    return transformed;
  }

  public static final VoxelShape getUnionShape(AxisAlignedBB ... aabbs)
  {
    VoxelShape shape = VoxelShapes.empty();
    for(AxisAlignedBB aabb: aabbs) shape = VoxelShapes.combine(shape, VoxelShapes.create(aabb), IBooleanFunction.OR);
    return shape;
  }

  public static final VoxelShape getUnionShape(AxisAlignedBB[] ... aabb_list)
  {
    VoxelShape shape = VoxelShapes.empty();
    for(AxisAlignedBB[] aabbs:aabb_list) {
      for(AxisAlignedBB aabb: aabbs) shape = VoxelShapes.combine(shape, VoxelShapes.create(aabb), IBooleanFunction.OR);
    }
    return shape;
  }

  public static final class BlockPosRange implements Iterable<BlockPos>
  {
    private final int x0, x1, y0, y1, z0, z1;

    public BlockPosRange(int x0, int y0, int z0, int x1, int y1, int z1)
    {
      this.x0 = Math.min(x0,x1); this.x1 = Math.max(x0,x1);
      this.y0 = Math.min(y0,y1); this.y1 = Math.max(y0,y1);
      this.z0 = Math.min(z0,z1); this.z1 = Math.max(z0,z1);
    }

    public static final BlockPosRange of(AxisAlignedBB range)
    {
      return new BlockPosRange(
        (int)Math.floor(range.minX),
        (int)Math.floor(range.minY),
        (int)Math.floor(range.minZ),
        (int)Math.floor(range.maxX-.0625),
        (int)Math.floor(range.maxY-.0625),
        (int)Math.floor(range.maxZ-.0625)
      );
    }

    public int getXSize()
    { return x1-x0+1; }

    public int getYSize()
    { return y1-y0+1; }

    public int getZSize()
    { return z1-z0+1; }

    public int getArea()
    { return getXSize() * getZSize(); }

    public int getHeight()
    { return getYSize(); }

    public int getVolume()
    { return getXSize() * getYSize() * getZSize(); }

    public BlockPos byXZYIndex(int xyz_index)
    {
      final int xsz=getXSize(), ysz=getYSize(), zsz=getZSize();
      xyz_index = xyz_index % (xsz*ysz*zsz);
      final int y = xyz_index / (xsz*zsz);
      xyz_index -= y * (xsz*zsz);
      final int z = xyz_index / xsz;
      xyz_index -= z * xsz;
      final int x = xyz_index;
      return new BlockPos(x0+x, y0+y, z0+z);
    }

    public BlockPos byXZIndex(int xz_index, int y_offset)
    {
      final int xsz=getXSize(), zsz=getZSize();
      xz_index = xz_index % (xsz*zsz);
      final int z = xz_index / xsz;
      xz_index -= z * xsz;
      final int x = xz_index;
      return new BlockPos(x0+x, y0+y_offset, z0+z);
    }

    public static final class BlockRangeIterator implements Iterator<BlockPos>
    {
      private final BlockPosRange range_;
      private int x,y,z;

      public BlockRangeIterator(BlockPosRange range)
      { range_ = range; x=range.x0; y=range.y0; z=range.z0; }

      @Override
      public boolean hasNext()
      { return (z <= range_.z1); }

      @Override
      public BlockPos next()
      {
        if(!hasNext()) throw new NoSuchElementException();
        final BlockPos pos = new BlockPos(x,y,z);
        ++x;
        if(x > range_.x1) {
          x = range_.x0;
          ++y;
          if(y > range_.y1) {
            y = range_.y0;
            ++z;
          }
        }
        return pos;
      }
    }

    @Override
    public BlockRangeIterator iterator()
    { return new BlockRangeIterator(this); }

    public Stream<BlockPos> stream()
    { return java.util.stream.StreamSupport.stream(spliterator(), false); }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // JAR resource related
  // -------------------------------------------------------------------------------------------------------------------

  public static String loadResourceText(InputStream is)
  {
    try {
      if(is==null) return "";
      BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
      return br.lines().collect(Collectors.joining("\n"));
    } catch(Throwable e) {
      return "";
    }
  }

  public static String loadResourceText(String path)
  { return loadResourceText(Auxiliaries.class.getResourceAsStream(path)); }

  public static void logGitVersion(String mod_name)
  {
    try {
      // Done during construction to have an exact version in case of a crash while registering.
      String version = Auxiliaries.loadResourceText("/.gitversion-" + modid).trim();
      logInfo(mod_name+((version.isEmpty())?(" (dev build)"):(" GIT id #"+version)) + ".");
    } catch(Throwable e) {
      // (void)e; well, then not. Priority is not to get unneeded crashes because of version logging.
    }
  }
}
