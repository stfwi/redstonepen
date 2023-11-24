/*
 * @file Networking.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Main client/server message handling.
 */
package wile.redstonepen.libmc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


public class Networking
{
  public static void init(String modid, final RegisterPayloadHandlerEvent event)
  {
    final IPayloadRegistrar registrar = event.registrar(modid);

    PacketTileNotifyClientToServer.PacketData.PACKET_ID = new ResourceLocation(modid, "tnc2s");
    registrar.play(PacketTileNotifyClientToServer.PacketData.PACKET_ID, PacketTileNotifyClientToServer.PacketData::new, hnd->hnd.server(PacketTileNotifyClientToServer::onReceive));
    PacketTileNotifyServerToClient.PacketData.PACKET_ID = new ResourceLocation(modid, "tns2c");
    registrar.play(PacketTileNotifyServerToClient.PacketData.PACKET_ID, PacketTileNotifyServerToClient.PacketData::new, hnd->hnd.client(PacketTileNotifyServerToClient::onReceive));

    PacketContainerSyncClientToServer.PacketData.PACKET_ID = new ResourceLocation(modid, "csc2s");
    registrar.play(PacketContainerSyncClientToServer.PacketData.PACKET_ID, PacketContainerSyncClientToServer.PacketData::new, hnd->hnd.server(PacketContainerSyncClientToServer::onReceive));
    PacketContainerSyncServerToClient.PacketData.PACKET_ID = new ResourceLocation(modid, "css2c");
    registrar.play(PacketContainerSyncServerToClient.PacketData.PACKET_ID, PacketContainerSyncServerToClient.PacketData::new, hnd->hnd.client(PacketContainerSyncServerToClient::onReceive));

    PacketNbtNotifyClientToServer.PacketData.PACKET_ID = new ResourceLocation(modid, "nnc2s");
    registrar.play(PacketNbtNotifyClientToServer.PacketData.PACKET_ID, PacketNbtNotifyClientToServer.PacketData::new, hnd->hnd.server(PacketNbtNotifyClientToServer::onReceive));
    PacketNbtNotifyServerToClient.PacketData.PACKET_ID = new ResourceLocation(modid, "nns2c");
    registrar.play(PacketNbtNotifyServerToClient.PacketData.PACKET_ID, PacketNbtNotifyServerToClient.PacketData::new, hnd->hnd.client(PacketNbtNotifyServerToClient::onReceive));

    OverlayTextMessage.PacketData.PACKET_ID = new ResourceLocation(modid, "ols2c");
    registrar.play(OverlayTextMessage.PacketData.PACKET_ID, OverlayTextMessage.PacketData::new, hnd->hnd.client(OverlayTextMessage::onReceive));
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
    private record PacketData(BlockPos pos, CompoundTag nbt) implements CustomPacketPayload
    {
      private static ResourceLocation PACKET_ID;
      PacketData(final FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readNbt()); }
      @Override public ResourceLocation id() { return PACKET_ID; }
      @Override public void write(final FriendlyByteBuf buffer) { buffer.writeBlockPos(pos()); buffer.writeNbt(nbt()); }
    }

    private static void onReceive(final PacketData data, final PlayPayloadContext context)
    {
      final BlockPos pos = data.pos();
      final CompoundTag nbt = data.nbt();
      final Player player = context.player().orElse(null);
      final Level world = context.level().orElse(null);
      if((world==null) || (player==null)) return;
      context.workHandler().submitAsync(() -> {
        final BlockEntity te = world.getBlockEntity(pos);
        if(!(te instanceof IPacketTileNotifyReceiver tnr)) return;
        tnr.onClientPacketReceived(player, nbt);
      });
    }

    public static void sendToServer(BlockPos pos, CompoundTag nbt)
    {
      if((pos==null) || (nbt==null)) return;
      PacketDistributor.SERVER.noArg().send(new PacketData(pos, nbt));
    }

    public static void sendToServer(BlockEntity te, CompoundTag nbt)
    {
      if(te!=null) sendToServer(te.getBlockPos(), nbt);
    }
  }

  public static class PacketTileNotifyServerToClient
  {
    private record PacketData(BlockPos pos, CompoundTag nbt) implements CustomPacketPayload
    {
      private static ResourceLocation PACKET_ID;
      PacketData(final FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readNbt()); }
      @Override public ResourceLocation id() { return PACKET_ID; }
      @Override public void write(final FriendlyByteBuf buffer) { buffer.writeBlockPos(pos()); buffer.writeNbt(nbt()); }
    }

    private static void onReceive(final PacketData data, final PlayPayloadContext context)
    {
      final BlockPos pos = data.pos();
      final CompoundTag nbt = data.nbt();
      final Level world = context.level().orElse(null);
      if(world == null) return;
      context.workHandler().submitAsync(() -> {
        final BlockEntity te = world.getBlockEntity(pos);
        if(!(te instanceof Networking.IPacketTileNotifyReceiver tnr)) return;
        tnr.onServerPacketReceived(nbt);
      });
    }

    public static void sendToPlayer(ServerPlayer player, BlockEntity te, CompoundTag nbt)
    {
      if((te==null) || (nbt==null) || te.getLevel().isClientSide()) return;
      PacketDistributor.PLAYER.with(player).send(new PacketData(te.getBlockPos(), nbt));
    }

    public static void sendToPlayers(BlockEntity te, CompoundTag nbt)
    {
      if((te==null) || (!(te.getLevel() instanceof ServerLevel sl))) return;
      for(ServerPlayer player: sl.players()) {
        sendToPlayer(player, te, nbt);
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
    private record PacketData(int container_id, CompoundTag nbt) implements CustomPacketPayload
    {
      private static ResourceLocation PACKET_ID;
      PacketData(final FriendlyByteBuf buf) { this(buf.readInt(), buf.readNbt()); }
      @Override public ResourceLocation id() { return PACKET_ID; }
      @Override public void write(final FriendlyByteBuf buffer) { buffer.writeInt(container_id()); buffer.writeNbt(nbt()); }
    }

    private static void onReceive(final PacketData data, final PlayPayloadContext context)
    {
      final int cid = data.container_id();
      final CompoundTag nbt = data.nbt();
      final Player player = context.player().orElse(null);
      context.workHandler().submitAsync(() -> {
        if((player==null) || !(player.containerMenu instanceof INetworkSynchronisableContainer nsc)) return;
        if(player.containerMenu.containerId != cid) return;
        nsc.onClientPacketReceived(cid, player, nbt);
      });
    }

    public static void sendToServer(int container_id, CompoundTag nbt)
    {
      if(nbt==null) return;
      PacketDistributor.SERVER.noArg().send(new PacketData(container_id, nbt));
    }

    public static void sendToServer(AbstractContainerMenu container, CompoundTag nbt)
    {
      sendToServer(container.containerId, nbt);
    }
  }

  public static class PacketContainerSyncServerToClient
  {
    private record PacketData(int container_id, CompoundTag nbt) implements CustomPacketPayload
    {
      private static ResourceLocation PACKET_ID;
      PacketData(final FriendlyByteBuf buf) { this(buf.readInt(), buf.readNbt()); }
      @Override public ResourceLocation id() { return PACKET_ID; }
      @Override public void write(final FriendlyByteBuf buffer) { buffer.writeInt(container_id()); buffer.writeNbt(nbt()); }
    }

    private static void onReceive(final PacketData data, final PlayPayloadContext context)
    {
      final int cid = data.container_id();
      final CompoundTag nbt = data.nbt();
      final Player player = context.player().orElse(null);
      context.workHandler().submitAsync(() -> {
        if((player==null) || !(player.containerMenu instanceof Networking.INetworkSynchronisableContainer nsc)) return;
        if(player.containerMenu.containerId != cid) return;
        nsc.onServerPacketReceived(cid, nbt);
      });
    }

    public static void sendToPlayer(ServerPlayer player, int container_id, CompoundTag nbt)
    {
      if(nbt==null || player==null) return;
      PacketDistributor.PLAYER.with(player).send(new PacketData(container_id, nbt));
    }

    public static void sendToPlayer(ServerPlayer player, AbstractContainerMenu container, CompoundTag nbt)
    {
      if(container!=null) sendToPlayer(player, container.containerId, nbt);
    }

    public static <C extends AbstractContainerMenu & INetworkSynchronisableContainer>
    void sendToListeners(Level world, C container, CompoundTag nbt)
    {
      if(!(world instanceof ServerLevel sw)) return;
      for(ServerPlayer player: sw.players()) {
        if(player.containerMenu.containerId != container.containerId) continue;
        sendToPlayer(player, container.containerId, nbt);
      }
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // World notifications
  //--------------------------------------------------------------------------------------------------------------------

  public static class PacketNbtNotifyClientToServer
  {
    public static final Map<String, BiConsumer<Player, CompoundTag>> handlers = new HashMap<>();

    private record PacketData(CompoundTag nbt) implements CustomPacketPayload
    {
      private static ResourceLocation PACKET_ID;
      PacketData(final FriendlyByteBuf buf) { this(buf.readNbt()); }
      @Override public ResourceLocation id() { return PACKET_ID; }
      @Override public void write(final FriendlyByteBuf buffer) { buffer.writeNbt(nbt()); }
    }

    private static void onReceive(final PacketData data, final PlayPayloadContext context)
    {
      final CompoundTag nbt = data.nbt();
      final Player player = context.player().orElse(null);
      if((player==null) || (nbt==null)) return;
      final String hnd = nbt.getString("hnd");
      if(hnd.isEmpty() || (!handlers.containsKey(hnd))) return;
      context.workHandler().submitAsync(() -> handlers.get(hnd).accept(player, nbt));
    }

    public static void sendToServer(CompoundTag nbt)
    {
      if(nbt==null) return;
      PacketDistributor.SERVER.noArg().send(new PacketData(nbt));
    }
  }

  public static class PacketNbtNotifyServerToClient
  {
    public static final Map<String, Consumer<CompoundTag>> handlers = new HashMap<>();

    private record PacketData(CompoundTag nbt) implements CustomPacketPayload
    {
      private static ResourceLocation PACKET_ID;
      PacketData(final FriendlyByteBuf buf) { this(buf.readNbt()); }
      @Override public ResourceLocation id() { return PACKET_ID; }
      @Override public void write(final FriendlyByteBuf buffer) { buffer.writeNbt(nbt()); }
    }

    private static void onReceive(final PacketData data, final PlayPayloadContext context)
    {
      final CompoundTag nbt = data.nbt();
      if(nbt==null) return;
      final String hnd = nbt.getString("hnd");
      if(hnd.isEmpty() || (!handlers.containsKey(hnd))) return;
      context.workHandler().submitAsync(() -> handlers.get(hnd).accept(nbt));
    }

    public static void sendToPlayer(Player player, CompoundTag nbt)
    {
      if((nbt==null) || (!(player instanceof ServerPlayer sp))) return;
      PacketDistributor.PLAYER.with(sp).send(new PacketData(nbt));
    }

    public static void sendToPlayers(ServerLevel world, CompoundTag nbt)
    {
      if(world!=null) for(ServerPlayer player: world.players()) sendToPlayer(player, nbt);
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Main window GUI text message
  //--------------------------------------------------------------------------------------------------------------------

  public static class OverlayTextMessage
  {
    protected static BiConsumer<Component, Integer> handler_ = null;
    public static final int DISPLAY_TIME_MS = 3000;

    public record PacketData(Component message, int delay) implements CustomPacketPayload
    {
      private static ResourceLocation PACKET_ID;
      private static Component get_component(final FriendlyByteBuf buf)  { try { return buf.readComponent(); } catch(Throwable e) { return Component.translatable("[incorrect translation]"); } }
      PacketData(final FriendlyByteBuf buf) { this(get_component(buf), buf.readInt()); }
      @Override public ResourceLocation id() { return PACKET_ID; }
      @Override public void write(final FriendlyByteBuf buffer) { buffer.writeComponent(message()); buffer.writeInt(delay()); }
    }

    private static void onReceive(final PacketData data, final PlayPayloadContext context)
    {
      if(handler_ == null) return;
      final Component message = data.message();
      final int delay = data.delay();
      if(delay<=0) return;
      context.workHandler().submitAsync(() -> handler_.accept(message, delay));
    }

    public static void setHandler(BiConsumer<Component, Integer> handler)
    { if(handler_==null) handler_ = handler; }

    public static void sendToPlayer(ServerPlayer player, Component message)
    { sendToPlayer(player, message, DISPLAY_TIME_MS); }

    public static void sendToPlayer(ServerPlayer player, Component message, int delay)
    {
      if(Auxiliaries.isEmpty(message)) return;
      PacketDistributor.PLAYER.with(player).send(new PacketData(message, delay));
    }
  }

}
