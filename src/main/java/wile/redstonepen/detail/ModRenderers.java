/*
 * @file ModRenderers.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 */
package wile.redstonepen.detail;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import wile.redstonepen.ModConstants;
import wile.redstonepen.blocks.RedstoneTrack;
import wile.redstonepen.blocks.RedstoneTrack.defs.connections;
import wile.redstonepen.libmc.Auxiliaries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class ModRenderers
{
  @OnlyIn(Dist.CLIENT)
  public static class TrackTer implements BlockEntityRenderer<RedstoneTrack.TrackBlockEntity>
  {
    private static final ModelResourceLocation[] model_rls  = new ModelResourceLocation[RedstoneTrack.defs.STATE_FLAG_WIR_COUNT];
    private static final ModelResourceLocation[] modelm_rls = new ModelResourceLocation[RedstoneTrack.defs.STATE_FLAG_CON_COUNT];
    private static final ModelResourceLocation[] modelc_rls = new ModelResourceLocation[RedstoneTrack.defs.STATE_FLAG_CON_COUNT];
    private static final ArrayList<Vec3> power_rgb = new ArrayList<>();
    private static int tesr_error_counter = 4;
    private final BlockEntityRendererProvider.Context renderer_;

    public static List<ModelResourceLocation> registerModels()
    {
      List<ModelResourceLocation> resources_to_register = new ArrayList<>();

      RedstoneTrack.defs.models.STATE_WIRE_MAPPING.entrySet().forEach((kv->{
        final ModelResourceLocation mrl = new ModelResourceLocation(ResourceLocation.tryBuild(ModConstants.MODID, kv.getValue()).withPrefix("item/"), "standalone");
        for(int i=0; i<RedstoneTrack.defs.STATE_FLAG_WIR_COUNT; ++i) {
          if((kv.getKey() & (1L<<(RedstoneTrack.defs.STATE_FLAG_WIR_POS+i))) != 0) {
            model_rls[i] = mrl;
            break;
          }
        }
        resources_to_register.add(mrl); //  net.neoforged.client.model.ForgeModelBakery.addSpecialModel(mrl);
      }));
      RedstoneTrack.defs.models.STATE_CONNECT_MAPPING.entrySet().forEach((kv->{
        ModelResourceLocation mrl = new ModelResourceLocation(ResourceLocation.tryBuild(ModConstants.MODID, kv.getValue()).withPrefix("item/"), "standalone");
        for(int i=0; i<RedstoneTrack.defs.STATE_FLAG_CON_COUNT; ++i) {
          if((kv.getKey() & (1L<<(RedstoneTrack.defs.STATE_FLAG_CON_POS+i))) != 0) {
            modelc_rls[i] = mrl;
            break;
          }
        }
        resources_to_register.add(mrl);
      }));
      RedstoneTrack.defs.models.STATE_CNTWIRE_MAPPING.entrySet().forEach((kv->{
        ModelResourceLocation mrl = new ModelResourceLocation(ResourceLocation.tryBuild(ModConstants.MODID, kv.getValue()).withPrefix("item/"), "standalone");
        for(int i=0; i<RedstoneTrack.defs.STATE_FLAG_CON_COUNT; ++i) {
          if((kv.getKey() & (1L<<(RedstoneTrack.defs.STATE_FLAG_CON_POS+i))) != 0) {
            modelm_rls[i] = mrl;
            break;
          }
        }
        resources_to_register.add(mrl);
      }));
      power_rgb.clear();
      for(int i = 0; i <= 15; ++i) {
        float f = (float)i / 15.0f;
        power_rgb.add(new Vec3(
          Mth.clamp(0.01f + f, 0.0F, 1f),
          Mth.clamp(0.01f + f * 0.4f-.3f, 0.0F, 1f),
          Mth.clamp(0.01f + f * 0.4f-.2f, 0.0F, 1f)
        ));
      }
      return resources_to_register;
    }

    private static Vec3 getPowerRGB(int p)
    { return power_rgb.get(p & 0xf); }

    public TrackTer(BlockEntityRendererProvider.Context renderer)
    { this.renderer_ = renderer; }

    @Override
    @SuppressWarnings("deprecation")
    public void render(final RedstoneTrack.TrackBlockEntity te, float unused1, PoseStack mxs, MultiBufferSource buf, int combinedLightIn, int combinedOverlayIn)
    {
      if(tesr_error_counter <= 0) return;
      try {
        final BlockState block_state = te.getBlockState();
        final VertexConsumer vxb = buf.getBuffer(ItemBlockRenderTypes.getRenderType(block_state, false));
        combinedOverlayIn = OverlayTexture.pack(0, 0);
        mxs.pushPose();
        {
          final int wirfl = te.getWireFlags();
          final int wirfc = te.getWireFlagCount();
          long flag = 0x1;
          for(int i=0; i<wirfc; ++i, flag<<=1) {
            if((wirfl & flag) == 0) continue;
            final Vec3 rgb = getPowerRGB(te.getSidePower(connections.CONNECTION_BIT_ORDER[i/4]));
            final BakedModel model = Minecraft.getInstance().getModelManager().getModel(model_rls[i]);
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
              mxs.last(),
              vxb,
              null,
              model,
              (float)rgb.x(), (float)rgb.y(), (float)rgb.z(),
              combinedLightIn,
              combinedOverlayIn
            );
          }
        }
        {
          final int wirfl = te.getWireFlags();
          final int confl = te.getConnectionFlags();
          final int confc = te.getConnectionFlagCount();
          long wir = 0xfL;
          long con = 0x1L;
          for(int i=0; i<confc; ++i, con<<=1, wir<<=4) {
            if(((wirfl & wir)==0) && ((confl & con)==0)) continue;
            final Vec3 rgb = getPowerRGB(te.getSidePower(connections.CONNECTION_BIT_ORDER[i]));
            final BakedModel model = ((confl & con)==0)
              ? Minecraft.getInstance().getModelManager().getModel(modelm_rls[i])  // center model
              : Minecraft.getInstance().getModelManager().getModel(modelc_rls[i]); // connection blob model
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
              mxs.last(),
              vxb,
              null,
              model,
              (float)rgb.x(), (float)rgb.y(), (float)rgb.z(),
              combinedLightIn,
              combinedOverlayIn
            );
          }
        }
      } catch(Throwable e) {
        if(--tesr_error_counter<=0) {
          Auxiliaries.logError("TER was disabled because broken, exception was: " + e.getMessage());
          Auxiliaries.logError(String.join("\n", Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).toList()));
        }
      }
      mxs.popPose();
    }
  }

}
