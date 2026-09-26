package cn.blockforge.generated.sukunamod;

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

public final class DismantleRenderer extends EntityRenderer<DismantleProjectile> {
    /**
     * dismantle.png 的 4 帧动画图集（512x128，每帧 128x128）：墨刀从中心向两端
     * 生长（40% → 75% → 100%）后整条消散。斩击形状完全来自贴图本身，只画一个
     * 铺满整帧纹理的正方形面片——没有任何几何拼接缝，从来源上消除"断口/台阶"。
     * 飞行中的普通斩击投射物仍用这组墨刀动画（世界斩/网格斩用下方原画直贴）。
     */
    private static final ResourceLocation DISMANTLE_ANIM = new ResourceLocation(GeneratedMod.MOD_ID,
            "textures/entity/dismantle_anim.png");
    private static final int FRAMES = 4;
    /** 墨带在画布对角线上占 29/32（实测 x-y ∈ [-29,28]），据此反推面片边长。 */
    private static final float BAND_RATIO = 29.0F / 32.0F;
    /** 斩击贴图帧动画：用户绘制的四张斩击原画，依次播放 1→2→3→4 后消失。 */
    private static final ResourceLocation[] SLASH_FRAMES = {
            new ResourceLocation(GeneratedMod.MOD_ID, "textures/entity/slash_frame_1.png"),
            new ResourceLocation(GeneratedMod.MOD_ID, "textures/entity/slash_frame_2.png"),
            new ResourceLocation(GeneratedMod.MOD_ID, "textures/entity/slash_frame_3.png"),
            new ResourceLocation(GeneratedMod.MOD_ID, "textures/entity/slash_frame_4.png"),
    };
    /** 红边斩击：用户绘制的三帧原画（暗红刀身+亮红描边），按 1→2→3 播放。 */
    private static final ResourceLocation[] RED_EDGE_FRAMES = {
            new ResourceLocation(GeneratedMod.MOD_ID, "textures/entity/red_edge_frame_1.png"),
            new ResourceLocation(GeneratedMod.MOD_ID, "textures/entity/red_edge_frame_2.png"),
            new ResourceLocation(GeneratedMod.MOD_ID, "textures/entity/red_edge_frame_3.png"),
    };
    /** 红色斩击：用户绘制的三帧纯红原画，按 1→2→3 播放。 */
    private static final ResourceLocation[] RED_FRAMES = {
            new ResourceLocation(GeneratedMod.MOD_ID, "textures/entity/red_frame_1.png"),
            new ResourceLocation(GeneratedMod.MOD_ID, "textures/entity/red_frame_2.png"),
            new ResourceLocation(GeneratedMod.MOD_ID, "textures/entity/red_frame_3.png"),
    };
    /** 网格斩与世界斩直接使用的单帧原画：slash_frame_3，细长、清晰、无动画。 */
    private static final ResourceLocation SLASH_FRAME_3 = SLASH_FRAMES[2];
    /** 每张斩击贴图对应一个自定义无光照 RenderType，按纹理缓存复用。 */
    private static final Map<ResourceLocation, RenderType> UNLIT_TYPES = new ConcurrentHashMap<>();

    /**
     * 斩击专用 RenderType：走 position_tex_color 着色器——输出 = 贴图 × 顶点色。
     * 原版 entityTranslucent 的顶点着色器会把顶点色再乘一次
     * minecraft_mix_light(太阳方向, 法线)（约 0.2~1.55 倍）：我们面片法线恒为
     * (0,0,1)，正午太阳在头顶时只剩 0.55 倍，白色刀身被压成灰色——这就是
     * "斩击在一定高度/时间会变暗"的根源。该着色器同时不采样 lightmap、不乘雾，
     * 斩击在任何光照、任何高度、任何距离下都保持贴图原色。
     */
    private static RenderType slashType(ResourceLocation texture) {
        return UNLIT_TYPES.computeIfAbsent(texture, loc -> RenderType.create(
                "sukunamod_slash_unlit", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 256, true, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(
                                GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(loc, false, false))
                        .setTransparencyState(new RenderStateShard.TransparencyStateShard(
                                "slash_translucent_transparency",
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

    public DismantleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(DismantleProjectile entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        if (entity.isVisualOnly() && !entity.shouldRenderVisual(partialTick)) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
        pose.mulPose(Axis.ZP.rotation(entity.getSlashAngle()));
        float age = entity.getProjectileAge() + partialTick;
        if (entity.isVisualOnly()) {
            // 领域斩击与命中爆发：整幅贴用户绘制的斩击原画，寿命内按样式播完动画：
            // 原斩击 1→2→3→4，红边/红色斩击 1→2→3。连续解与领域斩击风暴每发随机
            // 挑选三种样式之一（getSlashStyle）。领域斩每刀长度 15~65 格随机，爆发固定 10 格。
            ResourceLocation[] frames = framesFor(entity.getSlashStyle());
            float lifetime = Math.max(1.0F, entity.getVisualLifetime());
            int frame = (int) (age * frames.length / lifetime);
            if (frame >= frames.length) {
                pose.popPose();
                return;
            }
            if (entity.isBurst()) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotation(entity.getBurstSeed()));
            }
            drawSlashFrame(pose, buffer, frames[frame], entity.getVisualLength());
            if (entity.isBurst()) pose.popPose();
        } else if (entity.isGridAttack()) {
            // 网格斩：9 横 9 竖全部由 slash_frame_3.png 单帧原画组成，每刀 9 格，
            // 细长清晰、静态直贴，不再走墨刀图集动画。
            drawGrid(pose, buffer);
        } else if (entity.isWorldSlash()) {
            // 世界斩：整幅 slash_frame_3.png 原画直接贴出去，一张贴图飞出去，
            // 不做任何加厚/UV 反算之类破坏贴图原画的操作，只是等比放大到 35 格刀长。
            drawFrameBlade(pose, buffer, (float) DismantleProjectile.WORLD_SLASH_BLADE_LENGTH);
        } else {
            // 其余飞行投射物：4 tick 内 1→2→3 帧，之后停在全宽
            // 帧；第 4 帧是消散帧，飞行途中不能停在半透明状态。
            int frame = Math.min(FRAMES - 2, (int) (age / 2.0F));
            float u0 = frame / (float) FRAMES;
            float u1 = (frame + 1) / (float) FRAMES;
            VertexConsumer consumer = buffer.getBuffer(slashType(DISMANTLE_ANIM));
            drawInkSlash(pose, consumer, u0, u1, 10.0F);
        }
        pose.popPose();
    }

    /** 按同步下来的样式选出该播哪一组斩击原画。 */
    private static ResourceLocation[] framesFor(int style) {
        if (style == 1) return RED_EDGE_FRAMES;
        if (style == 2) return RED_FRAMES;
        return SLASH_FRAMES;
    }

    /**
     * 把一张斩击原画整幅贴到正方形面片上。原画刀身沿左上→右下对角线贯穿画布，
     * 对角线长 = 边长 × √2，所以刀长 L 对应半边长 L/(2√2)。entityTranslucent 式
     * 的标准 alpha 混合且双面可见（不能用加法混合，否则黑芯会整条"空心"透明）。
     */
    private static void drawSlashFrame(PoseStack pose, MultiBufferSource buffer,
                                       ResourceLocation texture, float bladeLength) {
        pose.pushPose();
        VertexConsumer consumer = buffer.getBuffer(slashType(texture));
        float half = bladeLength / (float) (Math.sqrt(2.0D) * 2.0D);
        drawTexturedQuad(pose, consumer, half, half, 0.0F, 1.0F);
        pose.popPose();
    }

    /**
     * 画一记 dismantle.png 墨刀：单个正方形面片铺满图集当前帧，绕 Z 转 -45° 把
     * 画布右上→左下的对角线墨带摆正到局部 X 轴（斩击方向）。面片边长 =
     * 目标刀长 / 墨带占比，不剔背面，任何角度都可见。
     */
    private static void drawInkSlash(PoseStack pose, VertexConsumer consumer,
                                     float u0, float u1, float length) {
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
        float side = length / BAND_RATIO;
        float half = side * 0.5F;
        drawTexturedQuad(pose, consumer, half, half, u0, u1);
        pose.popPose();
    }

    /** 一个正方形面片，UV 横向取 [u0,u1)（图集当前帧），纵向铺满。 */
    private static void drawTexturedQuad(PoseStack pose, VertexConsumer consumer,
                                         float halfLength, float halfHeight, float u0, float u1) {
        Matrix4f matrix = pose.last().pose();
        texturedVertex(matrix, consumer, -halfLength, halfHeight, u0, 0.0F);
        texturedVertex(matrix, consumer, halfLength, halfHeight, u1, 0.0F);
        texturedVertex(matrix, consumer, halfLength, -halfHeight, u1, 1.0F);
        texturedVertex(matrix, consumer, -halfLength, -halfHeight, u0, 1.0F);
    }

    /**
     * POSITION_TEX_COLOR 顶点：贴图 × 顶点色即最终颜色，无光照/雾/方向光参与。
     * 注意：1.20.1 该格式的元素顺序是 [Position, UV, Vertex Color]，而 BufferBuilder
     * 对"当前元素 usage 不匹配"的填充调用会静默丢弃——必须按 Position→UV→Color
     * 的顺序写，先 color 会让 COLOR 元素始终空着，endVertex 抛
     * "Not filled all elements of the vertex"（普通解命中爆发崩溃的根源）。
     */
    private static void texturedVertex(Matrix4f matrix, VertexConsumer consumer,
                                       float x, float y, float u, float v) {
        consumer.vertex(matrix, x, y, 0.0F).uv(u, v).color(255, 255, 255, 255).endVertex();
    }

    /**
     * 画一记 slash_frame_3 原画刀：整幅贴到正方形面片上，绕 Z 转 +45° 把画布
     * 左上→右下的对角线刀身摆正到局部 X 轴（斩击方向）。面片对角线 = 目标刀长，
     * 边长 = 刀长/√2；原画本身就是贯穿画布的细线，任何长度下都保持清晰细长。
     */
    private static void drawFrameBlade(PoseStack pose, MultiBufferSource buffer, float bladeLength) {
        pose.pushPose();
        VertexConsumer consumer = buffer.getBuffer(slashType(SLASH_FRAME_3));
        pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
        float half = bladeLength / (float) (Math.sqrt(2.0D) * 2.0D);
        drawTexturedQuad(pose, consumer, half, half, 0.0F, 1.0F);
        pose.popPose();
    }

    /** 网格斩：9 横 9 竖，每格一记 9 格长的 slash_frame_3 单帧刀。 */
    private static void drawGrid(PoseStack pose, MultiBufferSource buffer) {
        for (int i = -4; i <= 4; i++) {
            pose.pushPose();
            pose.translate(0.0D, i, 0.0D);
            drawFrameBlade(pose, buffer, 9.0F);
            pose.popPose();
        }
        for (int i = -4; i <= 4; i++) {
            pose.pushPose();
            pose.translate(i, 0.0D, 0.0D);
            pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
            drawFrameBlade(pose, buffer, 9.0F);
            pose.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(DismantleProjectile entity) {
        return DISMANTLE_ANIM;
    }
}
