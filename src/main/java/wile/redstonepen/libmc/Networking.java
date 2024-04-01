/*
 * @file Networking.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Main client/server message handling.
 */
package wile.redstonepen.libmc;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.SimpleChannel;
import wile.redstonepen.ModConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


public class Networking
{
  private static SimpleChannel DEFAULT_CHANNEL;

  public static void init()
  {
    DEFAULT_CHANNEL = ChannelBuilder
            .named(new ResourceLocation(ModConstants.MODID, "default_ch"))
            .networkProtocolVersion(1)
            .simpleChannel();

    DEFAULT_CHANNEL.messageBuilder(PacketTileNotifyClientToServer.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder(PacketTileNotifyClientToServer::compose)
            .decoder(PacketTileNotifyClientToServer::parse)
            .consumerNetworkThread(PacketTileNotifyClientToServer.Handler::handle)
            .add();
    DEFAULT_CHANNEL.messageBuilder(PacketTileNotifyServerToClient.class, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(PacketTileNotifyServerToClient::compose)
            .decoder(PacketTileNotifyServerToClient::parse)
            .consumerNetworkThread(PacketTileNotifyServerToClient.Handler::handle)
            .add();
    DEFAULT_CHANNEL.messageBuilder(PacketContainerSyncClientToServer.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder(PacketContainerSyncClientToServer::compose)
            .decoder(PacketContainerSyncClientToServer::parse)
            .consumerNetworkThread(PacketContainerSyncClientToServer.Handler::handle)
            .add();
    DEFAULT_CHANNEL.messageBuilder(PacketContainerSyncServerToClient.class, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(PacketContainerSyncServerToClient::compose)
            .decoder(PacketContainerSyncServerToClient::parse)
            .consumerNetworkThread(PacketContainerSyncServerToClient.Handler::handle)
            .add();
    DEFAULT_CHANNEL.messageBuilder(PacketNbtNotifyClientToServer.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder(PacketNbtNotifyClientToServer::compose)
            .decoder(PacketNbtNotifyClientToServer::parse)
            .consumerNetworkThread(PacketNbtNotifyClientToServer.Handler::handle)
            .add();
    DEFAULT_CHANNEL.messageBuilder(PacketNbtNotifyServerToClient.class, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(PacketNbtNotifyServerToClient::compose)
            .decoder(PacketNbtNotifyServerToClient::parse)
            .consumerNetworkThread(PacketNbtNotifyServerToClient.Handler::handle)
            .add();
    DEFAULT_CHANNEL.messageBuilder(OverlayTextMessage.class, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(OverlayTextMessage::compose)
            .decoder(OverlayTextMessage::parse)
            .consumerNetworkThread(OverlayTextMessage.Handler::handle)
            .add();
  }

  @OnlyIn(Dist.CLIENT)
  private static Connection clientConnection()
  { return ((LocalPlayer)Auxiliaries.getClientPlayer()).connection.getConnection(); }

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
    CompoundTag nbt = null;
    BlockPos pos = BlockPos.ZERO;

    public static void sendToServer(BlockPos pos, CompoundTag nbt)
    { if((pos!=null) && (nbt!=null)) DEFAULT_CHANNEL.send(new PacketTileNotifyClientToServer(pos, nbt), clientConnection()); }

    public static void sendToServer(BlockEntity te, CompoundTag nbt)
    { if((te!=null) && (nbt!=null)) DEFAULT_CHANNEL.send(new PacketTileNotifyClientToServer(te, nbt), clientConnection()); }

    public PacketTileNotifyClientToServer()
    {}

    public PacketTileNotifyClientToServer(BlockPos pos, CompoundTag nbt)
    { this.nbt = nbt; this.pos = pos; }

    public PacketTileNotifyClientToServer(BlockEntity te, CompoundTag nbt)
    { this.nbt = nbt; pos = te.getBlockPos(); }

    public static PacketTileNotifyClientToServer parse(final FriendlyByteBuf buf)
    { return new PacketTileNotifyClientToServer(buf.readBlockPos(), buf.readNbt()); }

    public static void compose(final PacketTileNotifyClientToServer pkt, final FriendlyByteBuf buf)
    { buf.writeBlockPos(pkt.pos); buf.writeNbt(pkt.nbt); }

    public static class Handler
    {
      public static void handle(final PacketTileNotifyClientToServer pkt, final CustomPayloadEvent.Context ctx)
      {
        ctx.enqueueWork(() -> {
          Player player = ctx.getSender();
          if(player==null) return;
          Level world = player.level();
          final BlockEntity te = world.getBlockEntity(pkt.pos);
          if(!(te instanceof IPacketTileNotifyReceiver)) return;
          ((IPacketTileNotifyReceiver)te).onClientPacketReceived(ctx.getSender(), pkt.nbt);
        });
        ctx.setPacketHandled(true);
      }
    }
  }

  public static class PacketTileNotifyServerToClient
  {
    CompoundTag nbt = null;
    BlockPos pos = BlockPos.ZERO;

    public static void sendToPlayer(Player player, BlockEntity te, CompoundTag nbt)
    {
      if((!(player instanceof ServerPlayer splayer)) || (te==null) || (nbt==null)) return;
      DEFAULT_CHANNEL.send(new PacketTileNotifyServerToClient(te, nbt), splayer.connection.getConnection());
    }

    public static void sendToPlayers(BlockEntity te, CompoundTag nbt)
    {
      if(te==null || te.getLevel()==null) return;
      for(Player player: te.getLevel().players()) sendToPlayer(player, te, nbt);
    }

    public PacketTileNotifyServerToClient()
    {}

    public PacketTileNotifyServerToClient(BlockPos pos, CompoundTag nbt)
    { this.nbt=nbt; this.pos=pos; }

    public PacketTileNotifyServerToClient(BlockEntity te, CompoundTag nbt)
    { this.nbt=nbt; pos=te.getBlockPos(); }

    public static PacketTileNotifyServerToClient parse(final FriendlyByteBuf buf)
    { return new PacketTileNotifyServerToClient(buf.readBlockPos(), buf.readNbt()); }

    public static void compose(final PacketTileNotifyServerToClient pkt, final FriendlyByteBuf buf)
    { buf.writeBlockPos(pkt.pos); buf.writeNbt(pkt.nbt); }

    public static class Handler
    {
      public static void handle(final PacketTileNotifyServerToClient pkt, final CustomPayloadEvent.Context ctx)
      {
        Player player = Auxiliaries.getClientPlayer();
        if(player==null) return;
        ctx.enqueueWork(() -> {
          final Level world = player.level();
          if(world == null) return;
          final BlockEntity te = world.getBlockEntity(pkt.pos);
          if(!(te instanceof IPacketTileNotifyReceiver)) return;
          ((IPacketTileNotifyReceiver)te).onServerPacketReceived(pkt.nbt);
        });
        ctx.setPacketHandled(true);
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
    int id = -1;
    CompoundTag nbt = null;

    public static void sendToServer(int windowId, CompoundTag nbt)
    { if(nbt!=null) DEFAULT_CHANNEL.send(new PacketContainerSyncClientToServer(windowId, nbt), clientConnection()); }

    public static void sendToServer(AbstractContainerMenu container, CompoundTag nbt)
    { if(nbt!=null) DEFAULT_CHANNEL.send(new PacketContainerSyncClientToServer(container.containerId, nbt), clientConnection()); }

    public PacketContainerSyncClientToServer()
    {}

    public PacketContainerSyncClientToServer(int id, CompoundTag nbt)
    { this.nbt = nbt; this.id = id; }

    public static PacketContainerSyncClientToServer parse(final FriendlyByteBuf buf)
    { return new PacketContainerSyncClientToServer(buf.readInt(), buf.readNbt()); }

    public static void compose(final PacketContainerSyncClientToServer pkt, final FriendlyByteBuf buf)
    { buf.writeInt(pkt.id); buf.writeNbt(pkt.nbt); }

    public static class Handler
    {
      public static void handle(final PacketContainerSyncClientToServer pkt, final CustomPayloadEvent.Context ctx)
      {
        ctx.enqueueWork(() -> {
          final Player player = ctx.getSender();
          if((player==null) || !(player.containerMenu instanceof INetworkSynchronisableContainer)) return;
          if(player.containerMenu.containerId != pkt.id) return;
          ((INetworkSynchronisableContainer)player.containerMenu).onClientPacketReceived(pkt.id, player,pkt.nbt);
        });
        ctx.setPacketHandled(true);
      }
    }
  }

  public static class PacketContainerSyncServerToClient
  {
    int id = -1;
    CompoundTag nbt = null;

    public static void sendToPlayer(Player player, int windowId, CompoundTag nbt)
    {
      if((!(player instanceof ServerPlayer splayer)) || (nbt==null)) return;
      DEFAULT_CHANNEL.send(new PacketContainerSyncServerToClient(windowId, nbt), splayer.connection.getConnection());
    }

    public static void sendToPlayer(Player player, AbstractContainerMenu container, CompoundTag nbt)
    {
      if((!(player instanceof ServerPlayer splayer))  || (nbt==null)) return;
      DEFAULT_CHANNEL.send(new PacketContainerSyncServerToClient(container.containerId, nbt), splayer.connection.getConnection());
    }

    public static <C extends AbstractContainerMenu & INetworkSynchronisableContainer>
    void sendToListeners(Level world, C container, CompoundTag nbt)
    {
      for(Player player: world.players()) {
        if(player.containerMenu.containerId != container.containerId) continue;
        sendToPlayer(player, container.containerId, nbt);
      }
    }

    public PacketContainerSyncServerToClient()
    {}

    public PacketContainerSyncServerToClient(int id, CompoundTag nbt)
    { this.nbt=nbt; this.id=id; }

    public static PacketContainerSyncServerToClient parse(final FriendlyByteBuf buf)
    { return new PacketContainerSyncServerToClient(buf.readInt(), buf.readNbt()); }

    public static void compose(final PacketContainerSyncServerToClient pkt, final FriendlyByteBuf buf)
    { buf.writeInt(pkt.id); buf.writeNbt(pkt.nbt); }

    public static class Handler
    {
      public static void handle(final PacketContainerSyncServerToClient pkt, final CustomPayloadEvent.Context ctx)
      {
        ctx.enqueueWork(() -> {
          final Player player = Auxiliaries.getClientPlayer();
          if((player==null) || !(player.containerMenu instanceof INetworkSynchronisableContainer)) return;
          if(player.containerMenu.containerId != pkt.id) return;
          ((INetworkSynchronisableContainer)player.containerMenu).onServerPacketReceived(pkt.id,pkt.nbt);
        });
        ctx.setPacketHandled(true);
      }
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // World notifications
  //--------------------------------------------------------------------------------------------------------------------

  public static class PacketNbtNotifyClientToServer
  {
    public static final Map<String, BiConsumer<Player, CompoundTag>> handlers = new HashMap<>();
    final CompoundTag nbt;

    public static void sendToServer(CompoundTag nbt)
    { if(nbt!=null) DEFAULT_CHANNEL.send(new PacketNbtNotifyClientToServer(nbt), clientConnection()); }

    public PacketNbtNotifyClientToServer(CompoundTag nbt)
    { this.nbt = nbt; }

    public static PacketNbtNotifyClientToServer parse(final FriendlyByteBuf buf)
    { return new PacketNbtNotifyClientToServer(buf.readNbt()); }

    public static void compose(final PacketNbtNotifyClientToServer pkt, final FriendlyByteBuf buf)
    { buf.writeNbt(pkt.nbt); }

    public static class Handler
    {
      public static void handle(final PacketNbtNotifyClientToServer pkt, final CustomPayloadEvent.Context ctx)
      {
        ctx.enqueueWork(() -> {
          final ServerPlayer player = ctx.getSender();
          if(player==null) return;
          final String hnd = pkt.nbt.getString("hnd");
          if(hnd.isEmpty()) return;
          if(handlers.containsKey(hnd)) handlers.get(hnd).accept(player, pkt.nbt);
        });
        ctx.setPacketHandled(true);
      }
    }
  }

  public static class PacketNbtNotifyServerToClient
  {
    public static final Map<String, Consumer<CompoundTag>> handlers = new HashMap<>();
    final CompoundTag nbt;

    public static void sendToPlayer(Player player, CompoundTag nbt)
    {
      if((!(player instanceof ServerPlayer splayer))  || (nbt==null)) return;
      DEFAULT_CHANNEL.send(new PacketNbtNotifyServerToClient(nbt), splayer.connection.getConnection());
    }

    public static void sendToPlayers(Level world, CompoundTag nbt)
    { for(Player player: world.players()) sendToPlayer(player, nbt); }

    public PacketNbtNotifyServerToClient(CompoundTag nbt)
    { this.nbt = nbt; }

    public static PacketNbtNotifyServerToClient parse(final FriendlyByteBuf buf)
    { return new PacketNbtNotifyServerToClient(buf.readNbt()); }

    public static void compose(final PacketNbtNotifyServerToClient pkt, final FriendlyByteBuf buf)
    { buf.writeNbt(pkt.nbt); }

    public static class Handler
    {
      public static void handle(final PacketNbtNotifyServerToClient pkt, final CustomPayloadEvent.Context ctx)
      {
        ctx.enqueueWork(() -> {
          final String hnd = pkt.nbt.getString("hnd");
          if(hnd.isEmpty()) return;
          if(handlers.containsKey(hnd)) handlers.get(hnd).accept(pkt.nbt);
        });
        ctx.setPacketHandled(true);
      }
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Main window GUI text message
  //--------------------------------------------------------------------------------------------------------------------

  public static class OverlayTextMessage
  {
    public static final int DISPLAY_TIME_MS = 3000;
    private static BiConsumer<Component, Integer> handler_ = null;
    private final Component data_;
    private int delay_ = DISPLAY_TIME_MS;
    private Component data() { return data_; }
    private int delay() { return delay_; }

    public static void setHandler(BiConsumer<Component, Integer> handler)
    { if(handler_==null) handler_ = handler; }

    public static void sendToPlayer(Player player, Component message, int delay)
    {
      if((!(player instanceof ServerPlayer splayer)) || Auxiliaries.isEmpty(message)) return;
      DEFAULT_CHANNEL.send(new OverlayTextMessage(message, delay), splayer.connection.getConnection());
    }

    public OverlayTextMessage()
    { data_ = Component.translatable("[unset]"); }

    public OverlayTextMessage(final Component tct, int delay)
    { data_ = tct.copy(); delay_ = delay; }

    public static OverlayTextMessage parse(final FriendlyByteBuf buf)
    {
      try {
        return new OverlayTextMessage(buf.readComponent(), DISPLAY_TIME_MS);
      } catch(Throwable e) {
        return new OverlayTextMessage(Component.translatable("[incorrect translation]"), DISPLAY_TIME_MS);
      }
    }

    public static void compose(final OverlayTextMessage pkt, final FriendlyByteBuf buf)
    {
      try {
        buf.writeComponent(pkt.data());
      } catch(Throwable e) {
        Auxiliaries.logger().error("OverlayTextMessage.toBytes() failed: " + e);
      }
    }

    public static class Handler
    {
      public static void handle(final OverlayTextMessage pkt, final CustomPayloadEvent.Context ctx)
      {
        if(handler_ != null) ctx.enqueueWork(() -> handler_.accept(pkt.data(), pkt.delay()));
        ctx.setPacketHandled(true);
      }
    }
  }

}
