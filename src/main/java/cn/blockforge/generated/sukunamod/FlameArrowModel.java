package cn.blockforge.generated.sukunamod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

/** 灶·开火焰矢的立体体素网格，箭尖沿模型局部 +X 方向。 */
public final class FlameArrowModel {
    private static final float INV_16 = 1.0F / 16.0F;

    private FlameArrowModel() { }

    public static void render(PoseStack pose, VertexConsumer consumer, int light) {
        Matrix4f matrix = pose.last().pose();
        // 贴图 32x32 中的四个 8x8 色区：core、hot、flame、ember。
        cube(matrix, consumer, light, -8, -1.0F, -1.0F, 7, 1.0F, 1.0F,
                8F / 32F, 0F, 16F / 32F, 8F / 32F);
        cube(matrix, consumer, light, 5, -2.5F, -2.5F, 9, 2.5F, 2.5F,
                8F / 32F, 0F, 16F / 32F, 8F / 32F);
        cube(matrix, consumer, light, 9, -1.8F, -1.8F, 12, 1.8F, 1.8F,
                0F, 0F, 8F / 32F, 8F / 32F);
        cube(matrix, consumer, light, 12, -1.0F, -1.0F, 15, 1.0F, 1.0F,
                0F, 0F, 8F / 32F, 8F / 32F);
        cube(matrix, consumer, light, -15, -3.0F, -3.0F, -7, 3.0F, 3.0F,
                16F / 32F, 0F, 24F / 32F, 8F / 32F);
        cube(matrix, consumer, light, -16, -2.0F, -2.0F, -10, 2.0F, 2.0F,
                8F / 32F, 0F, 16F / 32F, 8F / 32F);
        cube(matrix, consumer, light, -15, 2.0F, -1.5F, -10, 4.5F, 1.5F,
                16F / 32F, 0F, 24F / 32F, 8F / 32F);
        cube(matrix, consumer, light, -15, -4.5F, -1.5F, -10, -2.0F, 1.5F,
                16F / 32F, 0F, 24F / 32F, 8F / 32F);
        cube(matrix, consumer, light, -15, -1.5F, 3.0F, -10, 1.5F, 4.5F,
                24F / 32F, 0F, 32F / 32F, 8F / 32F);
        cube(matrix, consumer, light, -20, -1.0F, -1.0F, -15, 1.0F, 1.0F,
                24F / 32F, 0F, 32F / 32F, 8F / 32F);
    }

    private static void cube(Matrix4f matrix, VertexConsumer consumer, int light,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float u1, float v1, float u2, float v2) {
        x1 *= INV_16;
        y1 *= INV_16;
        z1 *= INV_16;
        x2 *= INV_16;
        y2 *= INV_16;
        z2 *= INV_16;
        quad(matrix, consumer, light, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1,
                u1, v1, u2, v2, 0, 0, -1);
        quad(matrix, consumer, light, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2,
                u1, v1, u2, v2, 0, 0, 1);
        quad(matrix, consumer, light, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1,
                u1, v1, u2, v2, -1, 0, 0);
        quad(matrix, consumer, light, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2,
                u1, v1, u2, v2, 1, 0, 0);
        quad(matrix, consumer, light, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2,
                u1, v1, u2, v2, 0, -1, 0);
        quad(matrix, consumer, light, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1,
                u1, v1, u2, v2, 0, 1, 0);
    }

    private static void quad(Matrix4f matrix, VertexConsumer consumer, int light,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float u1, float v1, float u2, float v2,
                             float nx, float ny, float nz) {
        vertex(matrix, consumer, light, x1, y1, z1, u1, v1, nx, ny, nz);
        vertex(matrix, consumer, light, x2, y2, z2, u1, v2, nx, ny, nz);
        vertex(matrix, consumer, light, x3, y3, z3, u2, v2, nx, ny, nz);
        vertex(matrix, consumer, light, x4, y4, z4, u2, v1, nx, ny, nz);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer consumer, int light,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        consumer.vertex(matrix, x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(nx, ny, nz).endVertex();
    }
}
