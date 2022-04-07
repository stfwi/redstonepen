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

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RcaSync
{
  private static final String MESSAGE_HANDLER_ID = "rcadata";

  public static final class RcaData
  {
    public final UUID puid;
    public long last_update = 0;
    public long client_inputs = 0;
    public long client_outputs = 0;
    public long server_outputs = 0;

    public RcaData(UUID puid)
    { this.puid = puid; }

    public boolean isValid()
    { return (puid.getLeastSignificantBits()!=0) || (puid.getMostSignificantBits()!=0); }

    public String toString()
    {
      return "{player:\"" + ((puid == null) ? ("") : (puid.toString())) + "\", t:" + last_update +
        ", ci:" + String.format("%016x", client_inputs) + ", co:" + String.format("%016x", client_outputs) +
        ", so:" + String.format("%016x", server_outputs) + "}";
    }
  }

  public static final class CommonRca
  {
    private static final RcaData EMPTY = new RcaData(new UUID(0,0)); // intentionally not immutable, errors in the RCA may have unexpected behaviour, but shall not cause a game crash.
    private static final Map<UUID, RcaData> data_cache = new HashMap<>();
    private static long num_exceptions = 0;
    private static final long ERROR_CUTOFF_COUNT = 32;

    public static synchronized RcaData ofPlayer(@Nullable UUID puid)
    {
      if(puid == null) return EMPTY;
      if(!data_cache.containsKey(puid)) data_cache.put(puid, new RcaData(puid));
      return data_cache.getOrDefault(puid, EMPTY);
    }

    public static void init()
    {
      Networking.PacketNbtNotifyClientToServer.handlers.put(MESSAGE_HANDLER_ID, (player, nbt)->{
        // Function thread safe via Networking packet handler task queuing.
        try {
          if(num_exceptions < ERROR_CUTOFF_COUNT) {
            final RcaData rca = ofPlayer(player.getUUID());
            final boolean is_request = nbt.getBoolean("req");
            if(!is_request) {
              rca.last_update = System.currentTimeMillis();
              rca.client_inputs = nbt.getLong("i");
              rca.client_outputs = nbt.getLong("o");
            }
            if(is_request || (rca.client_outputs != rca.server_outputs)) {
              nbt.putLong("o", rca.server_outputs);
              Networking.PacketNbtNotifyServerToClient.sendToPlayer(player, nbt);
            }
          }
        } catch(Throwable ignored) {
          ++num_exceptions;
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
