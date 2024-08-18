/*
 * @file Networking.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Main client/server message handling.
 */
package wile.redstonepen.libmc;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import wile.redstonepen.ModConstants;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


public class Networking
{
  public static void init()
  {
    PayloadTypeRegistry.playC2S().register(UnifiedPayload.TYPE, UnifiedPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(UnifiedPayload.TYPE, UnifiedPayload.STREAM_CODEC);
    ServerPlayNetworking.registerGlobalReceiver(UnifiedPayload.TYPE, (unifed_payload, context)->{
      final ServerPlayer player = context.player();
      final ServerLevel world = player.serverLevel();
      if(player==null) return;
      final CompoundTag payload = unifed_payload.data().nbt();
      player.server.execute(()->{
        switch(unifed_payload.data().id()) {
          case PacketTileNotifyClientToServer.PACKET_ID -> {
            final BlockPos pos = BlockPos.of(payload.getLong("pos"));
            final CompoundTag nbt = payload.getCompound("nbt");
            final BlockEntity te = world.getBlockEntity(pos);
            if(!(te instanceof IPacketTileNotifyReceiver)) return;
            ((IPacketTileNotifyReceiver)te).onClientPacketReceived(player, nbt);
          }
          case PacketContainerSyncClientToServer.PACKET_ID -> {
            final int container_id = payload.getInt("cid");
            final CompoundTag nbt = payload.getCompound("nbt");
            if(!(player.containerMenu instanceof INetworkSynchronisableContainer nsc)) return;
            if(player.containerMenu.containerId != container_id) return;
            nsc.onClientPacketReceived(container_id, player, nbt);
          }
          case PacketNbtNotifyClientToServer.PACKET_ID -> {
            final String hnd = payload.getString("hnd");
            final CompoundTag nbt = payload.getCompound("nbt");
            if(hnd.isEmpty() || (!PacketNbtNotifyClientToServer.handlers.containsKey(hnd))) return;
            PacketNbtNotifyClientToServer.handlers.get(hnd).accept(player, nbt);
          }
        }
      });
    });
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Unified Packet Handling
  //--------------------------------------------------------------------------------------------------------------------

  public record UnifiedPayload(UnifiedData data) implements CustomPacketPayload
  {
    private static final StreamCodec<FriendlyByteBuf,UnifiedPayload> STREAM_CODEC = CustomPacketPayload.codec(UnifiedPayload::write, UnifiedPayload::new);
    private static final CustomPacketPayload.Type<UnifiedPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, "unpnbt"));

    public static CustomPacketPayload.Type<UnifiedPayload> getTYPE() { return TYPE; }
    private UnifiedPayload(FriendlyByteBuf buf) { this(new UnifiedData(buf.readUtf(), buf.readNbt())); }
    private void write(FriendlyByteBuf buf) {
      data.write(buf);
    }
    public CustomPacketPayload.Type<UnifiedPayload> type() { return TYPE; }

    public record UnifiedData(String id, CompoundTag nbt)
    {
      public UnifiedData(FriendlyByteBuf buf) { this(buf.readUtf(), buf.readNbt()); }
      public void write(FriendlyByteBuf buf) { buf.writeUtf(id); buf.writeNbt(nbt); }
      @Override public String toString() { return id + ": " + nbt.toString(); }
    }
  }

  private static void sendToClient(ServerPlayer player, String packet_id, CompoundTag payload_nbt)
  {
    ServerPlayNetworking.send(player, new UnifiedPayload(new UnifiedPayload.UnifiedData(packet_id, payload_nbt)));
  }

  private static void sendToClients(ServerLevel world, String packet_id, CompoundTag payload_nbt)
  {
    final var payload = new UnifiedPayload(new UnifiedPayload.UnifiedData(packet_id, payload_nbt));
    for(ServerPlayer player: world.players()) ServerPlayNetworking.send(player, payload);
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entity notifications
  //--------------------------------------------------------------------------------------------------------------------

  public interface IPacketTileNotifyReceiver
  {
    default void onServerPacketReceived(CompoundTag nbt) {}
    default void onClientPacketReceived(Player player, CompoundTag nbt) {}
  }

  public static class PacketTileNotifyClientToServer
  {
    protected static final String PACKET_ID = "tnc2s";
  }

  public static class PacketTileNotifyServerToClient
  {
    protected static final String PACKET_ID = "tns2c";

    public static void sendToPlayer(ServerPlayer player, BlockEntity te, CompoundTag nbt)
    {
      if((te==null) || (nbt==null)) return;
      final CompoundTag payload = new CompoundTag();
      payload.putLong("pos", te.getBlockPos().asLong());
      payload.put("nbt", nbt);
      sendToClient(player, PACKET_ID, payload);
    }

    public static void sendToPlayers(BlockEntity te, CompoundTag nbt)
    {
      if((te==null) || (!(te.getLevel() instanceof ServerLevel sworld))) return;
      final CompoundTag payload = new CompoundTag();
      payload.putLong("pos", te.getBlockPos().asLong());
      payload.put("nbt", nbt);
      sendToClients(sworld, PACKET_ID, payload);
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // (GUI) Container synchronization
  //--------------------------------------------------------------------------------------------------------------------

  public interface INetworkSynchronisableContainer
  {
    void onServerPacketReceived(int windowId, CompoundTag nbt);
    void onClientPacketReceived(int windowId, Player player, CompoundTag nbt);
  }

  public static class PacketContainerSyncClientToServer
  {
    protected static final String PACKET_ID = "csc2s";
  }

  public static class PacketContainerSyncServerToClient
  {
    protected static final String PACKET_ID = "css2c";

    public static void sendToPlayer(ServerPlayer player, int windowId, CompoundTag nbt)
    {
      if(nbt==null || player==null) return;
      final CompoundTag payload = new CompoundTag();
      payload.putInt("cid", windowId);
      payload.put("nbt", nbt);
      sendToClient(player, PACKET_ID, payload);
    }

    public static void sendToPlayer(ServerPlayer player, AbstractContainerMenu container, CompoundTag nbt)
    { if(container!=null) sendToPlayer(player, container.containerId, nbt); }

    public static <C extends AbstractContainerMenu & INetworkSynchronisableContainer>
    void sendToListeners(Level world, C container, CompoundTag nbt)
    {
      for(Player player: world.players()) {
        if(player.containerMenu.containerId != container.containerId) continue;
        sendToPlayer((ServerPlayer)player, container.containerId, nbt);
      }
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // World notifications
  //--------------------------------------------------------------------------------------------------------------------

  public static class PacketNbtNotifyClientToServer
  {
    protected static final String PACKET_ID = "nnc2s";
    public static final Map<String, BiConsumer<Player, CompoundTag>> handlers = new HashMap<>();
  }

  public static class PacketNbtNotifyServerToClient
  {
    protected static final String PACKET_ID = "nns2c";
    public static final Map<String, Consumer<CompoundTag>> handlers = new HashMap<>();

    public static void sendToPlayer(Player player, String handler, CompoundTag nbt)
    {
      if((nbt==null) || (handler==null) || (!(player instanceof ServerPlayer splayer))) return;
      final CompoundTag msg = new CompoundTag();
      msg.putString("hnd", handler);
      msg.put("nbt", nbt);
      sendToClient(splayer, PACKET_ID, msg);
    }

    public static void sendToPlayers(Level world, String handler, CompoundTag nbt)
    { if(world!=null) for(Player player: world.players()) sendToPlayer(player, handler, nbt); }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Main window GUI text message
  //--------------------------------------------------------------------------------------------------------------------

  public static class OverlayTextMessage
  {
    protected static final String PACKET_ID = "otms2c";
    protected static BiConsumer<Component, Integer> handler_ = null;
    public static final int DISPLAY_TIME_MS = 3000;

    public static void setHandler(BiConsumer<Component, Integer> handler)
    { if(handler_==null) handler_ = handler; }

    public static void sendToPlayer(ServerPlayer player, Component message)
    { sendToPlayer(player, message, DISPLAY_TIME_MS); }

    public static void sendToPlayer(ServerPlayer player, Component message, int delay)
    {
      if(Auxiliaries.isEmpty(message)) return;
      try {
        final CompoundTag payload = new CompoundTag();
        payload.putInt("delay", delay);
        payload.putString("msg", Auxiliaries.serializeTextComponent(message, player.registryAccess()));
        sendToClient(player, PACKET_ID, payload);
      } catch(Throwable e) {
        Auxiliaries.logger().error("OverlayTextMessage.toBytes() failed: " + e);
      }
    }
  }

}
