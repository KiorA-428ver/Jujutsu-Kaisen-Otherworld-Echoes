package cn.blockforge.generated.sukunamod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 隐形破坏斩击的渲染器：shouldRender 恒为 false，游戏内看不到它任何东西——
 * 它只是服务端用来触发方块破坏的高速斩击，视觉表现完全由射线投射的贴图帧动画负责。
 */
public final class TrenchSlashRenderer extends EntityRenderer<TrenchSlashEntity> {
    public TrenchSlashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(TrenchSlashEntity entity, Frustum camera,
                                double camX, double camY, double camZ) {
        return false;
    }

    @Override
    public void render(TrenchSlashEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
    }

    @Override
    public ResourceLocation getTextureLocation(TrenchSlashEntity entity) {
        return new ResourceLocation(GeneratedMod.MOD_ID, "textures/entity/dismantle_anim.png");
    }
}
