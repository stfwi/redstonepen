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
import wile.redstonepen.libmc.Auxiliaries;
import wile.redstonepen.libmc.Networking;
import wile.redstonepen.libmc.NetworkingClient;

import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RcaSync
{
  private static final String MESSAGE_HANDLER_ID = "rcadata";

  public static final class RcaData
  {
    public final UUID puid;
    public long client_inputs_ = 0;
    public long server_outputs_ = 0;

    public RcaData(UUID puid)
    { this.puid = puid; }

    public boolean isValid()
    { return (puid.getLeastSignificantBits()!=0) || (puid.getMostSignificantBits()!=0); }

    public synchronized long client_inputs()
    { return client_inputs_; }

    public synchronized void client_inputs(long val)
    { client_inputs_ = val; }

    public synchronized long server_outputs()
    { return server_outputs_; }

    public synchronized void server_outputs(long val)
    { server_outputs_ = val; }

    public String toString()
    {
      return "{player:\"" + ((puid == null) ? ("") : (puid.toString())) +
        ", ci:" + String.format("%016x", client_inputs_) + ", so:" + String.format("%016x", server_outputs_) + "}";
    }
  }

  public static final class CommonRca
  {
    public static final RcaData EMPTY = new RcaData(new UUID(0,0)); // intentionally not immutable, errors in the RCA may have unexpected behaviour, but shall not cause a game crash.
    private static final Map<UUID, RcaData> data_cache = new HashMap<>();
    private static long num_exceptions = 0;
    private static final long ERROR_CUTOFF_COUNT = 32;

    public static synchronized RcaData ofPlayer(@Nullable UUID puid, boolean allow_create)
    {
      if(puid == null) return EMPTY;
      if(allow_create && (!data_cache.containsKey(puid))) data_cache.put(puid, new RcaData(puid));
      return data_cache.getOrDefault(puid, EMPTY);
    }

    public static void init()
    {
      Networking.PacketNbtNotifyClientToServer.handlers.put(MESSAGE_HANDLER_ID, (player, nbt)->{
        // Function thread safe via Networking packet handler task queuing.
        if((!nbt.contains("i")) || (num_exceptions >= ERROR_CUTOFF_COUNT)) return;
        try {
          final RcaData rca = ofPlayer(player.getUUID(), true);
          rca.client_inputs(nbt.getLong("i"));
          nbt.remove("i");
          nbt.putLong("o", rca.server_outputs());
          Networking.PacketNbtNotifyServerToClient.sendToPlayer(player, nbt);
        } catch(Throwable ignored) {
          ++num_exceptions;
        }
      });
    }
  }

  public static final class ClientRca
  {
    public static boolean init()
    {
      final wile.api.rca.RedstoneClientAdapter rca = wile.api.rca.FmmRedstoneClientAdapter.Adapter.instance();
      if(rca == null) {
        Auxiliaries.logInfo("Redstone Pen RCA disabled (default).");
        return false;
      }
      Networking.PacketNbtNotifyServerToClient.handlers.put(MESSAGE_HANDLER_ID, (nbt)->{
        if(nbt.contains("o")) rca.setOutputs(nbt.getLong("o"));
      });
      Auxiliaries.logInfo("Redstone Pen RCA detected and enabled on this client machine.");
      return true;
    }

    public static void tick()
    {
      final wile.api.rca.RedstoneClientAdapter rca = wile.api.rca.FmmRedstoneClientAdapter.Adapter.instance();
      if(rca == null) return;
      rca.tick();
      CompoundTag nbt = new CompoundTag();
      nbt.putString("hnd", MESSAGE_HANDLER_ID);
      nbt.putLong("i", rca.getInputs());
      NetworkingClient.PacketNbtNotifyClientToServer.sendToServer(nbt);
      rca.setInputsChanged(false);
    }
  }
}
