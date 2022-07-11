/*
 * @file SidedProxy.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * General client/server sideness selection proxy.
 */
package wile.redstonepen.libmc;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.DistExecutor;
import javax.annotation.Nullable;

public class SidedProxy
{
  @Nullable
  public static Player getPlayerClientSide()
  { return proxy.getPlayerClientSide(); }

  @Nullable
  public static Level getWorldClientSide()
  { return proxy.getWorldClientSide(); }

  // --------------------------------------------------------------------------------------------------------

  private static final ISidedProxy proxy = DistExecutor.unsafeRunForDist(()->ClientProxy::new, ()->ServerProxy::new);

  private interface ISidedProxy
  {
    default @Nullable Player getPlayerClientSide() { return null; }
    default @Nullable Level getWorldClientSide() { return null; }
  }

  private static final class ClientProxy implements ISidedProxy
  {
    public @Nullable Player getPlayerClientSide() { return Minecraft.getInstance().player; }
    public @Nullable Level getWorldClientSide() { return Minecraft.getInstance().level; }
  }

  private static final class ServerProxy implements ISidedProxy
  {
  }

}
