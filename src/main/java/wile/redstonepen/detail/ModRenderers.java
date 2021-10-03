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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import wile.redstonepen.ModRedstonePen;
import wile.redstonepen.blocks.RedstoneTrack;
import wile.redstonepen.blocks.RedstoneTrack.defs.connections;

import java.util.ArrayList;


public class ModRenderers
{
  @OnlyIn(Dist.CLIENT)
  public static class TrackTer implements BlockEntityRenderer<RedstoneTrack.TrackTileEntity>
  {
    private static final ModelResourceLocation[] model_rls  = new ModelResourceLocation[RedstoneTrack.defs.STATE_FLAG_WIR_COUNT];
    private static final ModelResourceLocation[] modelm_rls = new ModelResourceLocation[RedstoneTrack.defs.STATE_FLAG_CON_COUNT];
    private static final ModelResourceLocation[] modelc_rls = new ModelResourceLocation[RedstoneTrack.defs.STATE_FLAG_CON_COUNT];
    private static final ArrayList<Vec3> power_rgb = new ArrayList<>();
    private static int tesr_error_counter = 4;
    private final BlockEntityRendererProvider.Context renderer_;

    public static void registerModels()
    {
      RedstoneTrack.defs.models.STATE_WIRE_MAPPING.entrySet().forEach((kv->{
        ModelResourceLocation mrl = new ModelResourceLocation(new ResourceLocation(ModRedstonePen.MODID, kv.getValue()), "inventory");
        for(int i=0; i<RedstoneTrack.defs.STATE_FLAG_WIR_COUNT; ++i) {
          if((kv.getKey() & (1L<<(RedstoneTrack.defs.STATE_FLAG_WIR_POS+i))) != 0) {
            model_rls[i] = mrl;
            break;
          }
        }
        net.minecraftforge.client.model.ModelLoader.addSpecialModel(mrl);
      }));
      RedstoneTrack.defs.models.STATE_CONNECT_MAPPING.entrySet().forEach((kv->{
        ModelResourceLocation mrl = new ModelResourceLocation(new ResourceLocation(ModRedstonePen.MODID, kv.getValue()), "inventory");
        for(int i=0; i<RedstoneTrack.defs.STATE_FLAG_CON_COUNT; ++i) {
          if((kv.getKey() & (1L<<(RedstoneTrack.defs.STATE_FLAG_CON_POS+i))) != 0) {
            modelc_rls[i] = mrl;
            break;
          }
        }
        net.minecraftforge.client.model.ModelLoader.addSpecialModel(mrl);
      }));
      RedstoneTrack.defs.models.STATE_CNTWIRE_MAPPING.entrySet().forEach((kv->{
        ModelResourceLocation mrl = new ModelResourceLocation(new ResourceLocation(ModRedstonePen.MODID, kv.getValue()), "inventory");
        for(int i=0; i<RedstoneTrack.defs.STATE_FLAG_CON_COUNT; ++i) {
          if((kv.getKey() & (1L<<(RedstoneTrack.defs.STATE_FLAG_CON_POS+i))) != 0) {
            modelm_rls[i] = mrl;
            break;
          }
        }
        net.minecraftforge.client.model.ModelLoader.addSpecialModel(mrl);
      }));
      power_rgb.clear();
      for(int i = 0; i <= 15; ++i) {
        float f = (float)i / 15.0f;
        if(false) {
          power_rgb.add(new Vec3(
            Mth.clamp(0.01f + f, 0.0F, 1f),
            Mth.clamp(0.01f + f * 0.4f-.3f, 0.0F, 1f),
            Mth.clamp(0.01f + f * 0.4f-.2f, 0.0F, 1f)
          ));
        } else {
          power_rgb.add(new Vec3(
            Mth.clamp(f * 0.6F + (f > 0.0F ? 0.4F : 0.3F), 0.0F, 1.0F),
            Mth.clamp(f * f * 0.7F - 0.5F, 0.0F, 1.0F),
            Mth.clamp(f * f * 0.6F - 0.7F, 0.0F, 1.0F)
          ));
        }
      }
    }

    private static Vec3 getPowerRGB(int p)
    { return power_rgb.get(p & 0xf); }

    public TrackTer(BlockEntityRendererProvider.Context renderer)
    { this.renderer_ = renderer; }

    @Override
    @SuppressWarnings("deprecation")
    public void render(final RedstoneTrack.TrackTileEntity te, float unused1, PoseStack mxs, MultiBufferSource buf, int combinedLightIn, int combinedOverlayIn)
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
              combinedOverlayIn,
              net.minecraftforge.client.model.data.EmptyModelData.INSTANCE
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
              combinedOverlayIn,
              net.minecraftforge.client.model.data.EmptyModelData.INSTANCE
            );
          }
        }
        mxs.popPose();
      } catch(Throwable e) {
        if(--tesr_error_counter<=0) {
          ModRedstonePen.logger().error("TER was disabled because broken, exception was: " + e.getMessage());
          ModRedstonePen.logger().error(e.getStackTrace());
        }
      }
    }
  }

}