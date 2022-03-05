/*
 * @file RcaProxy.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Dedicated Redstone Client Adapter Proxy.
 */
package wile.redstonepen.detail;

import net.minecraft.nbt.CompoundTag;
import wile.redstonepen.libmc.detail.Networking;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RcaSync
{
  private static final String MESSAGE_HANDLER_ID = "rcadata";

  public static final class RcaData
  {
    public long client_inputs = 0;
    public long client_outputs = 0;
    public long server_outputs = 0;
    public boolean client_inputs_dirty = false;
    public boolean server_outputs_dirty = true;
  }

  public static final class CommonRca
  {
    private static final Map<UUID, RcaData> data_cache = new HashMap<>();

    public static void init()
    {
      Networking.PacketNbtNotifyClientToServer.handlers.put(MESSAGE_HANDLER_ID, (player, nbt)->{
        // Function thread safe via Networking packet handler task queuing.
        if(!data_cache.containsKey(player.getUUID())) { data_cache.put(player.getUUID(), new RcaData()); }
        final RcaData rca = data_cache.getOrDefault(player.getUUID(), new RcaData());
        final boolean is_request = nbt.getBoolean("req");
        if(!is_request) {
          rca.client_inputs = nbt.getLong("i");
          rca.client_outputs = nbt.getLong("o");
          rca.client_inputs_dirty = true;
        }
        if(is_request || rca.server_outputs_dirty) {
          nbt.putLong("o", rca.server_outputs);
          rca.server_outputs_dirty = false;
          Networking.PacketNbtNotifyServerToClient.sendToPlayer(player, nbt);
        }
      });
    }
  }

  public static final class ClientRca
  {

    public static void init()
    {
      final wile.api.rca.RedstoneClientAdapter rca = wile.api.rca.FmmRedstoneClientAdapter.Adapter.instance();
      if(rca == null) return;
      Networking.PacketNbtNotifyServerToClient.handlers.put(MESSAGE_HANDLER_ID, (nbt)->rca.setOutputs(nbt.getLong("o")));
    }

    public static void tick()
    {
      if(!wile.api.rca.FmmRedstoneClientAdapter.Adapter.available()) return;
      final wile.api.rca.RedstoneClientAdapter rca = wile.api.rca.FmmRedstoneClientAdapter.Adapter.instance();
      if(rca == null) return;
      rca.tick();
      if(!rca.isInputsChanged()) return;
      rca.setInputsChanged(false);
      CompoundTag nbt = new CompoundTag();
      nbt.putString("hnd", MESSAGE_HANDLER_ID);
      nbt.putLong("i", rca.getInputs());
      nbt.putLong("o", rca.getOutputs());
      Networking.PacketNbtNotifyClientToServer.sendToServer(nbt);
    }
  }
}
