package cn.blockforge.generated.sukunamod;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灶·开火焰箭渲染器：不再使用火焰矢立体模型，改用两张带 4 帧循环动画的贴图
 * 沿飞行轴交叉叠合——flame_arrow_anim.png（火矢1~4 原帧，箭尖朝上）铺在竖直面，
 * flame_arrow_anim_rot.png（每帧顺时针 90° 翻转，箭尖朝右）铺在水平面，
 * 两个平面共用同一个帧序号，动画严格同步。一轮循环 24 tick（1.2 秒），一帧 6 tick。
 * 箭体走 position_tex_color 无光照着色器保持贴图原色（近白焰心亮度极高，装光影后
 * 自然触发 bloom）；外面再叠两层加色混合（SRC_ALPHA, ONE）的放大副本构成外发光光晕，
 * 不装光影也肉眼可见。
 */
public final class FlameArrowRenderer extends EntityRenderer<FlameArrowEntity> {
    /** 竖直面贴图：360x1440 帧条，火矢1→4 自上而下排列，箭尖在每帧顶部。 */
    private static final ResourceLocation TEXTURE_V = new ResourceLocation(
            GeneratedMod.MOD_ID, "textures/entity/flame_arrow_anim.png");
    /** 水平面贴图：同一批帧各顺时针 90° 翻转，箭尖在每帧右缘。 */
    private static final ResourceLocation TEXTURE_H = new ResourceLocation(
            GeneratedMod.MOD_ID, "textures/entity/flame_arrow_anim_rot.png");
    private static final int FRAMES = 4;
    /** 每帧 6 tick：4 帧 × 6 = 24 tick = 1.2 秒一轮循环。 */
    private static final long FRAME_TICKS = 6L;
    /** 交叉面边长（方块）；贴图近正方形，箭身约占其中 90% 长度。 */
    private static final float PLANE = 1.9F;
    /** 帧条切帧内缩（像素）：防止相邻帧渗色。 */
    private static final float INSET_U = 1.0F / 360.0F;
    private static final float INSET_V = 1.0F / 1440.0F;
    /** 外发光两档放大倍率与暖色光晕强度（乘进贴图，alpha 控制加色量）。 */
    private static final float GLOW_TIGHT = 1.45F;
    private static final float GLOW_WIDE = 2.1F;

    private static final Map<ResourceLocation, RenderType> BODY_TYPES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> GLOW_TYPES = new ConcurrentHashMap<>();

    /** 箭体贴图类型：无光照、alpha 混合、不剔除，输出 = 贴图 × 顶点色。 */
    private static RenderType bodyType(ResourceLocation texture) {
        return BODY_TYPES.computeIfAbsent(texture, loc -> RenderType.create(
                "sukunamod_flame_arrow_body", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 256, true, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(
                                GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(loc, false, false))
                        .setTransparencyState(new RenderStateShard.TransparencyStateShard(
                                "flame_arrow_translucent",
                                () -> {
                                    RenderSystem.enableBlend();
                                    RenderSystem.defaultBlendFunc();
                                },
                                () -> RenderSystem.disableBlend()))
                        .setCullState(new RenderStateShard.CullStateShard(false))
                        .setLightmapState(new RenderStateShard.LightmapStateShard(false))
                        .setOverlayState(new RenderStateShard.OverlayStateShard(false))
                        .createCompositeState(true)));
    }

    /** 外发光类型：加色混合（目标色 += 贴图色 × alpha），亮部叠加出光晕。 */
    private static RenderType glowType(ResourceLocation texture) {
        return GLOW_TYPES.computeIfAbsent(texture, loc -> RenderType.create(
                "sukunamod_flame_arrow_glow", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 256, true, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(
                                GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(loc, false, false))
                        .setTransparencyState(new RenderStateShard.TransparencyStateShard(
                                "flame_arrow_additive",
                                () -> {
                                    RenderSystem.enableBlend();
                                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                                            GlStateManager.DestFactor.ONE);
                                },
                                () -> RenderSystem.disableBlend()))
                        .setCullState(new RenderStateShard.CullStateShard(false))
                        .setLightmapState(new RenderStateShard.LightmapStateShard(false))
                        .setOverlayState(new RenderStateShard.OverlayStateShard(false))
                        .createCompositeState(true)));
    }

    public FlameArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(FlameArrowEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        pose.pushPose();
        // 与原模型一致的朝向变换：局部 +X = 飞行方向（箭尖）。
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F - entity.getYRot()));
        // 实体抬头时 XRot 为负值，所以这里取反，箭尖才能随视线抬起。
        pose.mulPose(Axis.ZP.rotationDegrees(-entity.getXRot()));
        // 全局 tick 取帧：两张贴图共用同一帧序号，动画帧严格同步。
        int frame = (int) ((entity.level().getGameTime() / FRAME_TICKS) % FRAMES);
        float vTop = frame / (float) FRAMES + INSET_V;
        float vBottom = (frame + 1) / (float) FRAMES - INSET_V;
        float uLeft = INSET_U;
        float uRight = 1.0F - INSET_U;

        // 外发光先画（加色叠加在背景上），再由箭体压出清晰焰形。
        drawCross(pose, buffer.getBuffer(glowType(TEXTURE_V)),
                PLANE * GLOW_WIDE, vTop, vBottom, uLeft, uRight, 255, 150, 70, 90, true);
        drawCross(pose, buffer.getBuffer(glowType(TEXTURE_H)),
                PLANE * GLOW_WIDE, vTop, vBottom, uLeft, uRight, 255, 150, 70, 90, false);
        drawCross(pose, buffer.getBuffer(glowType(TEXTURE_V)),
                PLANE * GLOW_TIGHT, vTop, vBottom, uLeft, uRight, 255, 210, 150, 150, true);
        drawCross(pose, buffer.getBuffer(glowType(TEXTURE_H)),
                PLANE * GLOW_TIGHT, vTop, vBottom, uLeft, uRight, 255, 210, 150, 150, false);
        // 箭体：竖直面用原帧贴图，水平面用 90° 翻转贴图，交叉成"更立体"的双面火矢。
        drawCross(pose, buffer.getBuffer(bodyType(TEXTURE_V)),
                PLANE, vTop, vBottom, uLeft, uRight, 255, 255, 255, 255, true);
        drawCross(pose, buffer.getBuffer(bodyType(TEXTURE_H)),
                PLANE, vTop, vBottom, uLeft, uRight, 255, 255, 255, 255, false);
        pose.popPose();
    }

    /**
     * 画一张交叉面片。verticalPlane=true 时面片落在 XY 平面（用原帧贴图，箭尖在
     * 图像顶部 → +X）；false 时落在 XZ 平面（用 90° 翻转贴图，箭尖在图像右缘 → +X）。
     */
    private static void drawCross(PoseStack pose, VertexConsumer consumer, float size,
                                  float vTop, float vBottom, float uLeft, float uRight,
                                  int red, int green, int blue, int alpha, boolean verticalPlane) {
        Matrix4f matrix = pose.last().pose();
        float half = size / 2.0F;
        if (verticalPlane) {
            consumer.vertex(matrix, half, -half, 0.0F).uv(uLeft, vTop)
                    .color(red, green, blue, alpha).endVertex();
            consumer.vertex(matrix, half, half, 0.0F).uv(uRight, vTop)
                    .color(red, green, blue, alpha).endVertex();
            consumer.vertex(matrix, -half, half, 0.0F).uv(uRight, vBottom)
                    .color(red, green, blue, alpha).endVertex();
            consumer.vertex(matrix, -half, -half, 0.0F).uv(uLeft, vBottom)
                    .color(red, green, blue, alpha).endVertex();
        } else {
            consumer.vertex(matrix, half, 0.0F, -half).uv(uRight, vTop)
                    .color(red, green, blue, alpha).endVertex();
            consumer.vertex(matrix, half, 0.0F, half).uv(uRight, vBottom)
                    .color(red, green, blue, alpha).endVertex();
            consumer.vertex(matrix, -half, 0.0F, half).uv(uLeft, vBottom)
                    .color(red, green, blue, alpha).endVertex();
            consumer.vertex(matrix, -half, 0.0F, -half).uv(uLeft, vTop)
                    .color(red, green, blue, alpha).endVertex();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(FlameArrowEntity entity) {
        return TEXTURE_V;
    }
}
