/*
 * @file ModRedstonePen.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.CreativeModeTabs;
import wile.redstonepen.libmc.Auxiliaries;
import wile.redstonepen.libmc.Networking;
import wile.redstonepen.libmc.Registries;


public class ModRedstonePen implements ModInitializer
{
  public static final String MODID = "redstonepen";
  public static final String MODNAME = "Redstone Pen";

  public ModRedstonePen()
  {
    Auxiliaries.init(MODID, CompoundTag::new);
    Auxiliaries.logGitVersion(MODNAME);
  }

  public void onInitialize()
  {
    Registries.init(MODID, "quill");
    Networking.init(MODID);
    ModContent.init(MODID);
    ModContent.initReferences();
    wile.redstonepen.detail.RcaSync.CommonRca.init();
    ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(reg-> Registries.getRegisteredItems().forEach(reg::accept) );
  }
}
