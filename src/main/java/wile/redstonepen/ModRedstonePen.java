/*
 * @file ModRedstonePen.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.CreativeModeTabs;
import wile.redstonepen.libmc.Auxiliaries;
import wile.redstonepen.libmc.Networking;
import wile.redstonepen.libmc.Registries;


public class ModRedstonePen implements ModInitializer
{
  public ModRedstonePen()
  {
    Auxiliaries.init();
    Auxiliaries.logGitVersion();
  }

  public void onInitialize()
  {
    Registries.init("quill");
    Networking.init();
    ModContent.init();
    ModContent.initReferences();
    wile.redstonepen.detail.RcaSync.CommonRca.init();
    ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(reg-> Registries.getRegisteredItems().forEach(reg::accept) );
  }
}
