/*
 * @file SidedProxy.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * General client/server sideness selection proxy.
 */
package wile.redstonepen.libmc.detail;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.DistExecutor;
import javax.annotation.Nullable;
import java.util.Optional;

public class SidedProxy
{
  @Nullable
  public static PlayerEntity getPlayerClientSide()
  { return proxy.getPlayerClientSide(); }

  @Nullable
  public static World getWorldClientSide()
  { return proxy.getWorldClientSide(); }

  @Nullable
  public static Minecraft mc()
  { return proxy.mc(); }

  @Nullable
  public static Optional<Boolean> isCtrlDown()
  { return proxy.isCtrlDown(); }

  @Nullable
  public static Optional<Boolean> isShiftDown()
  { return proxy.isShiftDown(); }

  public static Optional<String> getClipboard()
  { return proxy.getClipboard(); }

  public static boolean setClipboard(String text)
  { return proxy.setClipboard(text); }

  // --------------------------------------------------------------------------------------------------------

  private static final ISidedProxy proxy = DistExecutor.unsafeRunForDist(()->ClientProxy::new, ()->ServerProxy::new);

  private interface ISidedProxy
  {
    default @Nullable PlayerEntity getPlayerClientSide() { return null; }
    default @Nullable World getWorldClientSide() { return null; }
    default @Nullable Minecraft mc() { return null; }
    default Optional<Boolean> isCtrlDown() { return Optional.empty(); }
    default Optional<Boolean> isShiftDown() { return Optional.empty(); }
    default Optional<String> getClipboard() { return Optional.empty(); }
    default boolean setClipboard(String text) { return false; }
  }

  private static final class ClientProxy implements ISidedProxy
  {
    public @Nullable PlayerEntity getPlayerClientSide() { return Minecraft.getInstance().player; }
    public @Nullable World getWorldClientSide() { return Minecraft.getInstance().level; }
    public @Nullable Minecraft mc() { return Minecraft.getInstance(); }
    public Optional<Boolean> isCtrlDown() { return Optional.of(Auxiliaries.isCtrlDown()); }
    public Optional<Boolean> isShiftDown() { return Optional.of(Auxiliaries.isShiftDown()); }
    public Optional<String> getClipboard() { return (mc()==null) ? Optional.empty() : Optional.of(net.minecraft.client.gui.fonts.TextInputUtil.getClipboardContents(mc())); }
    public boolean setClipboard(String text) { if(mc()==null) {return false;} net.minecraft.client.gui.fonts.TextInputUtil.setClipboardContents(Minecraft.getInstance(), text); return true; }
  }

  private static final class ServerProxy implements ISidedProxy
  {
  }

}
