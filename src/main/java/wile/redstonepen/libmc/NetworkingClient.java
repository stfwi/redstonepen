/*
 * @file Networking.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Main client/server message handling.
 */
package wile.redstonepen.libmc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;


@Environment(EnvType.CLIENT)
public class NetworkingClient
{
  public static void clientInit(String modid)
  {
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(Networking.PacketTileNotifyServerToClient.PACKET_ID, (client, handler, buf, responseSender)->{
      BlockPos pos = buf.readBlockPos();
      CompoundTag nbt = buf.readNbt();
      client.execute(()->{
        Level world = net.minecraft.client.Minecraft.getInstance().level;
        if(world == null) return;
        final BlockEntity te = world.getBlockEntity(pos);
        if(!(te instanceof Networking.IPacketTileNotifyReceiver)) return;
        ((Networking.IPacketTileNotifyReceiver)te).onServerPacketReceived(nbt);
      });
    });
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(Networking.PacketContainerSyncServerToClient.PACKET_ID, (client, handler, buf, responseSender)->{
      final int container_id = buf.readInt();
      final CompoundTag nbt = buf.readNbt();
      client.execute(()->{
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if((player==null) || !(player.containerMenu instanceof Networking.INetworkSynchronisableContainer)) return;
        if(player.containerMenu.containerId != container_id) return;
        ((Networking.INetworkSynchronisableContainer)player.containerMenu).onServerPacketReceived(container_id, nbt);
      });
    });
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(Networking.PacketNbtNotifyServerToClient.PACKET_ID, (client, handler, buf, responseSender)->{
      final CompoundTag nbt = buf.readNbt();
      if(nbt==null) return;
      final String hnd = nbt.getString("hnd");
      if(hnd.isEmpty() || (!Networking.PacketNbtNotifyServerToClient.handlers.containsKey(hnd))) return;
      client.execute(()->Networking.PacketNbtNotifyServerToClient.handlers.get(hnd).accept(nbt));
    });
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(Networking.OverlayTextMessage.PACKET_ID, (client, handler, buf, responseSender)->{
      if(Networking.OverlayTextMessage.handler_ == null) return;
      Component m;
      try { m = buf.readComponent(); } catch(Throwable e) { m = Component.translatable("[incorrect translation]"); }
      final Component message = m;
      final int delay = buf.readInt();
      if(delay<=0) return;
      client.execute(()->Networking.OverlayTextMessage.handler_.accept(message, delay));
    });
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entity notifications
  //--------------------------------------------------------------------------------------------------------------------

  @Environment(EnvType.CLIENT)
  public static class PacketTileNotifyClientToServer extends Networking.PacketTileNotifyClientToServer
  {
    public static void sendToServer(BlockPos pos, CompoundTag nbt)
    {
      if((pos==null) || (nbt==null)) return;
      FriendlyByteBuf buf = PacketByteBufs.create();
      buf.writeBlockPos(pos);
      buf.writeNbt(nbt);
      net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(PACKET_ID, buf);
    }

    public static void sendToServer(BlockEntity te, CompoundTag nbt)
    { if(te!=null) sendToServer(te.getBlockPos(), nbt); }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // (GUI) Container synchronization
  //--------------------------------------------------------------------------------------------------------------------

  @Environment(EnvType.CLIENT)
  public static class PacketContainerSyncClientToServer extends Networking.PacketContainerSyncClientToServer
  {
    public static void sendToServer(int windowId, CompoundTag nbt)
    {
      if(nbt==null) return;
      FriendlyByteBuf buf = PacketByteBufs.create();
      buf.writeInt(windowId);
      buf.writeNbt(nbt);
      net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(PACKET_ID, buf);
    }

    public static void sendToServer(AbstractContainerMenu container, CompoundTag nbt)
    { sendToServer(container.containerId, nbt); }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // World notifications
  //--------------------------------------------------------------------------------------------------------------------

  @Environment(EnvType.CLIENT)
  public static class PacketNbtNotifyClientToServer extends Networking.PacketNbtNotifyClientToServer
  {
    public static void sendToServer(CompoundTag nbt)
    {
      if(nbt==null) return;
      FriendlyByteBuf buf = PacketByteBufs.create();
      buf.writeNbt(nbt);
      net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(PACKET_ID, buf);
    }
  }


}
