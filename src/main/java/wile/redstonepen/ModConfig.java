/*
 * @file ModConfig.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Main class for module settings. Handles reading and
 * saving the config file.
 */
package wile.redstonepen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.logging.log4j.Logger;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;


public class ModConfig
{
  private static final Logger LOGGER = ModRedstonePen.LOGGER;
  private static final String MODID = ModRedstonePen.MODID;
  public static final CommonConfig COMMON;
  public static final ForgeConfigSpec COMMON_CONFIG_SPEC;

  static {
    final Pair<CommonConfig, ForgeConfigSpec> common_ = (new ForgeConfigSpec.Builder()).configure(CommonConfig::new);
    COMMON_CONFIG_SPEC = common_.getRight();
    COMMON = common_.getLeft();
  }

  //--------------------------------------------------------------------------------------------------------------------

  public static class CommonConfig
  {
    // Optout
    public final ForgeConfigSpec.ConfigValue<String> pattern_excludes;
    public final ForgeConfigSpec.ConfigValue<String> pattern_includes;
    // MISC
    public final ForgeConfigSpec.BooleanValue with_experimental;
    public final ForgeConfigSpec.BooleanValue with_config_logging;
    /// Tweaks
    public final ForgeConfigSpec.IntValue max_pen_redstone_capacity;


    CommonConfig(ForgeConfigSpec.Builder builder)
    {
      builder.comment("Settings affecting the logical server side.")
        .push("server");
      // --- OPTOUTS ------------------------------------------------------------
      {
        builder.comment("Opt-out settings")
          .push("optout");
        pattern_excludes = builder
          .translation(MODID + ".config.pattern_excludes")
          .comment("Opt-out any block by its registry name ('*' wildcard matching, "
            + "comma separated list, whitespaces ignored. You must match the whole name, "
            + "means maybe add '*' also at the begin and end. Example: '*wood*,*steel*' "
            + "excludes everything that has 'wood' or 'steel' in the registry name. "
            + "The matching result is also traced in the log file. ")
          .define("pattern_excludes", "");
        pattern_includes = builder
          .translation(MODID + ".config.pattern_includes")
          .comment("Prevent blocks from being opt'ed by registry name ('*' wildcard matching, "
            + "comma separated list, whitespaces ignored. Evaluated before all other opt-out checks. "
            + "You must match the whole name, means maybe add '*' also at the begin and end. Example: "
            + "'*wood*,*steel*' includes everything that has 'wood' or 'steel' in the registry name."
            + "The matching result is also traced in the log file.")
          .define("pattern_includes", "");
        builder.pop();
      }
      // --- MISC ---------------------------------------------------------------
      {
        builder.comment("Miscellaneous settings")
          .push("miscellaneous");
        with_experimental = builder
          .translation(MODID + ".config.with_experimental")
          .comment("Enables experimental features. Use at own risk.")
          .define("with_experimental", false);
        with_config_logging = builder
          .translation(MODID + ".config.with_config_logging")
          .comment("Enable detailed logging of the config values and resulting calculations in each mod feature config.")
          .define("with_config_logging", false);
        builder.pop();
      }
      // --- TWEAKS ------------------------------------------------------------
      {
        builder.comment("Tweak settings")
          .push("tweaks");
        max_pen_redstone_capacity = builder
          .translation(MODID + ".config.max_pen_redstone_capacity")
          .comment("Defines how much redstone can be stored in the pen. Use 0 to force using redstone from your inventory.")
          .defineInRange("max_pen_redstone_capacity", 0, 0, 256);
        builder.pop();
      }
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Optout checks
  //--------------------------------------------------------------------------------------------------------------------

  public static boolean isOptedOut(final @Nullable Block block)
  { return isOptedOut(block.asItem()); }

  public static boolean isOptedOut(final @Nullable Item item)
  { return (item!=null) && optouts_.contains(item.getRegistryName().getPath()); }

  public static boolean withExperimental()
  { return with_experimental_features_; }

  public static boolean withoutRecipes()
  { return false; }

  public static boolean withDebugLogging()
  { return with_experimental_features_ && with_config_logging_; }

  //--------------------------------------------------------------------------------------------------------------------
  // Cache
  //--------------------------------------------------------------------------------------------------------------------

  private static final CompoundTag server_config_ = new CompoundTag();
  private static HashSet<String> optouts_ = new HashSet<>();
  private static boolean with_experimental_features_ = false;
  private static boolean with_config_logging_ = false;

  public static CompoundTag getServerConfig()
  { return server_config_; }

  private static void updateOptouts()
  {
    final ArrayList<String> includes = new ArrayList<>();
    final ArrayList<String> excludes = new ArrayList<>();
    {
      String inc = COMMON.pattern_includes.get().toLowerCase().replaceAll(MODID+":", "").replaceAll("[^*_,a-z0-9]", "");
      if(!COMMON.pattern_includes.get().equals(inc)) COMMON.pattern_includes.set(inc);
      String[] incl = inc.split(",");
      for(int i=0; i< incl.length; ++i) {
        incl[i] = incl[i].replaceAll("[*]", ".*?");
        if(!incl[i].isEmpty()) includes.add(incl[i]);
      }
    }
    {
      String exc = COMMON.pattern_excludes.get().toLowerCase().replaceAll(MODID+":", "").replaceAll("[^*_,a-z0-9]", "");
      String[] excl = exc.split(",");
      for(int i=0; i< excl.length; ++i) {
        excl[i] = excl[i].replaceAll("[*]", ".*?");
        if(!excl[i].isEmpty()) excludes.add(excl[i]);
      }
    }
    if(!excludes.isEmpty()) log("Config pattern excludes: '" + String.join(",", excludes) + "'");
    if(!includes.isEmpty()) log("Config pattern includes: '" + String.join(",", includes) + "'");
    {
      HashSet<String> optouts = new HashSet<>();
      ModContent.getRegisteredItems().stream().filter((Item item) -> {
        if(item==null) return true;
        try {
          final String rn = item.getRegistryName().getPath();
          try {
            for(String e : includes) {
              if(rn.matches(e)) {
                return false;
              }
            }
            for(String e : excludes) {
              if(rn.matches(e)) {
                return true;
              }
            }
          } catch(Throwable ex) {
            LOGGER.error("optout include pattern failed, disabling.");
            includes.clear();
            excludes.clear();
          }
        } catch(Exception ex) {
          LOGGER.error("Exception evaluating the optout config: '"+ex.getMessage()+"'");
        }
        return false;
      }).forEach(e -> optouts.add(e.getRegistryName().getPath()));
      ModContent.getRegisteredBlocks().stream().filter(e->(e==null)||isOptedOut(e.asItem())).forEach(e->optouts.add(e.getRegistryName().getPath()));
      optouts_ = optouts;
    }
    {
      String s = String.join(",", optouts_);
      server_config_.putString("optout", s);
      if(!s.isEmpty()) log("Opt-outs:" + s);
    }
  }

  public static void apply()
  {
    if((!COMMON_CONFIG_SPEC.isLoaded())) return;
    with_config_logging_ = COMMON.with_config_logging.get();
    with_experimental_features_ = COMMON.with_experimental.get();
    if(with_experimental_features_) LOGGER.info("Config: EXPERIMENTAL FEATURES ENABLED.");
    updateOptouts();
    //RedstonePenItem.on_config(COMMON.max_pen_redstone_capacity.get());
  }

  public static void log(String config_message)
  {
    if(!with_config_logging_) return;
    LOGGER.info(config_message);
  }
}
