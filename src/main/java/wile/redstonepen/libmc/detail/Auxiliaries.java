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
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Auxiliaries
{
  private static String modid;
  private static Logger logger;
  private static Supplier<CompoundNBT> server_config_supplier = ()->new CompoundNBT();
  private static boolean dev_mode = false;

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
  { return dev_mode; }

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

  public static final AxisAlignedBB getRotatedAABB(AxisAlignedBB bb, Direction new_facing)
  { return getRotatedAABB(bb, new_facing, false); }

  public static final AxisAlignedBB[] getRotatedAABB(AxisAlignedBB[] bb, Direction new_facing)
  { return getRotatedAABB(bb, new_facing, false); }

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
