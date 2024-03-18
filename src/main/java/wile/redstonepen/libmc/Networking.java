/*
 * @file Networking.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Main client/server message handling.
 */
package wile.redstonepen.libmc;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


public class Networking
{
  public static void init(String modid)
  {
    PacketTileNotifyClientToServer.PACKET_ID = new ResourceLocation(modid, "tenc2s");
    PacketTileNotifyServerToClient.PACKET_ID = new ResourceLocation(modid, "tens2c");
    ServerPlayNetworking.registerGlobalReceiver(PacketTileNotifyClientToServer.PACKET_ID, (server, player, handler, buf, responseSender)->{
      final BlockPos pos = buf.readBlockPos();
      final CompoundTag nbt = buf.readNbt();
      server.execute(()->{
        if(player==null) return;
        Level world = player.level();
        final BlockEntity te = world.getBlockEntity(pos);
        if(!(te instanceof IPacketTileNotifyReceiver)) return;
        ((IPacketTileNotifyReceiver)te).onClientPacketReceived(player, nbt);
      });
    });
    PacketContainerSyncClientToServer.PACKET_ID = new ResourceLocation(modid, "csc2s");
    PacketContainerSyncServerToClient.PACKET_ID = new ResourceLocation(modid, "css2c");
    ServerPlayNetworking.registerGlobalReceiver(PacketContainerSyncClientToServer.PACKET_ID, (server, player, handler, buf, responseSender)->{
      final int container_id = buf.readInt();
      final CompoundTag nbt = buf.readNbt();
      server.execute(()->{
        if((player==null) || !(player.containerMenu instanceof INetworkSynchronisableContainer)) return;
        if(player.containerMenu.containerId != container_id) return;
        ((INetworkSynchronisableContainer)player.containerMenu).onClientPacketReceived(container_id, player, nbt);
      });
    });
    PacketNbtNotifyClientToServer.PACKET_ID = new ResourceLocation(modid, "nnc2s");
    PacketNbtNotifyServerToClient.PACKET_ID = new ResourceLocation(modid, "nns2c");
    ServerPlayNetworking.registerGlobalReceiver(PacketNbtNotifyClientToServer.PACKET_ID, (server, player, handler, buf, responseSender)->{
      final CompoundTag nbt = buf.readNbt();
      if((player==null) || (nbt==null)) return;
      final String hnd = nbt.getString("hnd");
      if(hnd.isEmpty() || (!PacketNbtNotifyClientToServer.handlers.containsKey(hnd))) return;
      server.execute(()->PacketNbtNotifyClientToServer.handlers.get(hnd).accept(player, nbt));
    });
    OverlayTextMessage.PACKET_ID = new ResourceLocation(modid, "otms2c");
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
    protected static ResourceLocation PACKET_ID;
  }

  public static class PacketTileNotifyServerToClient
  {
    protected static ResourceLocation PACKET_ID;

    public static void sendToPlayer(ServerPlayer player, BlockEntity te, CompoundTag nbt)
    {
      if((te==null) || (nbt==null)) return;
      FriendlyByteBuf buf = PacketByteBufs.create();
      buf.writeBlockPos(te.getBlockPos());
      buf.writeNbt(nbt);
      ServerPlayNetworking.send(player, PACKET_ID, buf);
    }

    public static void sendToPlayers(BlockEntity te, CompoundTag nbt)
    {
      if((te==null) || (te.getLevel()==null) || te.getLevel().isClientSide()) return;
      for(Player player: te.getLevel().players()) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(te.getBlockPos());
        buf.writeNbt(nbt);
        ServerPlayNetworking.send((ServerPlayer)player, PACKET_ID, buf);
      }
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
    protected static ResourceLocation PACKET_ID;
  }

  public static class PacketContainerSyncServerToClient
  {
    protected static ResourceLocation PACKET_ID;

    public static void sendToPlayer(ServerPlayer player, int windowId, CompoundTag nbt)
    {
      if(nbt==null || player==null) return;
      FriendlyByteBuf buf = PacketByteBufs.create();
      buf.writeInt(windowId);
      buf.writeNbt(nbt);
      ServerPlayNetworking.send(player, PACKET_ID, buf);
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
    protected static ResourceLocation PACKET_ID;
    public static final Map<String, BiConsumer<Player, CompoundTag>> handlers = new HashMap<>();
  }

  public static class PacketNbtNotifyServerToClient
  {
    protected static ResourceLocation PACKET_ID;
    public static final Map<String, Consumer<CompoundTag>> handlers = new HashMap<>();

    public static void sendToPlayer(Player player, CompoundTag nbt)
    {
      if((nbt==null) || (!(player instanceof ServerPlayer))) return;
      FriendlyByteBuf buf = PacketByteBufs.create();
      buf.writeNbt(nbt);
      ServerPlayNetworking.send((ServerPlayer)player, PACKET_ID, buf);
    }

    public static void sendToPlayers(Level world, CompoundTag nbt)
    { if(world!=null) for(Player player: world.players()) sendToPlayer(player, nbt); }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Main window GUI text message
  //--------------------------------------------------------------------------------------------------------------------

  public static class OverlayTextMessage
  {
    protected static ResourceLocation PACKET_ID;
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
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeComponent(message);
        buf.writeInt(delay);
        ServerPlayNetworking.send(player, PACKET_ID, buf);
      } catch(Throwable e) {
        Auxiliaries.logger().error("OverlayTextMessage.toBytes() failed: " + e);
      }
    }
  }

}
