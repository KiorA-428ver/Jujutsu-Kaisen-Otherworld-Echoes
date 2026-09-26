import json
from pathlib import Path

src = Path("src/main/resources/assets/sukunamod/models/entity/shrine.bbmodel")
out = Path("src/main/java/cn/blockforge/generated/sukunamod/ShrineModel.java")
data = json.loads(src.read_text(encoding="utf-8"))

faces = ("north", "east", "south", "west", "up", "down")
def uv(face):
    values = face["uv"]
    return "uv(%s, %s, %s, %s)" % tuple(f"{float(v):g}F" for v in values)

def f(v):
    return f"{float(v):g}F"

cubes = []
for e in data["elements"]:
    a = e["from"]
    b = e["to"]
    uvs = [uv(e["faces"][name]) for name in faces]
    cubes.append("            cube(%s, %s, %s, %s, %s, %s, %s)," % (
        f(a[0]), f(a[1]), f(a[2]), f(b[0]), f(b[1]), f(b[2]), ", ".join(uvs)))

source = '''package cn.blockforge.generated.sukunamod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

/** Runtime mesh exported from the authored Sukuna shrine Blockbench model. */
public final class ShrineModel {
    private static final float TEXTURE_SIZE = 16.0F;
    private static final Cube[] CUBES = {
%s    };

    private ShrineModel() { }

    public static void render(PoseStack pose, VertexConsumer consumer, int packedLight) {
        Matrix4f matrix = pose.last().pose();
        for (Cube cube : CUBES) cube.render(matrix, consumer, packedLight);
    }

    private static Cube cube(float x1, float y1, float z1, float x2, float y2, float z2,
                             Uv north, Uv east, Uv south, Uv west, Uv top, Uv bottom) {
        return new Cube(x1, y1, z1, x2, y2, z2, north, east, south, west, top, bottom);
    }

    private static Uv uv(float u1, float v1, float u2, float v2) {
        return new Uv(u1 / TEXTURE_SIZE, v1 / TEXTURE_SIZE,
                u2 / TEXTURE_SIZE, v2 / TEXTURE_SIZE);
    }

    private record Uv(float u1, float v1, float u2, float v2) { }

    private static final class Cube {
        private final float x1, y1, z1, x2, y2, z2;
        private final Uv north, east, south, west, top, bottom;

        private Cube(float x1, float y1, float z1, float x2, float y2, float z2,
                     Uv north, Uv east, Uv south, Uv west, Uv top, Uv bottom) {
            this.x1 = (x1 - 32.0F) / 16.0F;
            this.y1 = y1 / 16.0F;
            this.z1 = (z1 - 32.0F) / 16.0F;
            this.x2 = (x2 - 32.0F) / 16.0F;
            this.y2 = y2 / 16.0F;
            this.z2 = (z2 - 32.0F) / 16.0F;
            this.north = north;
            this.east = east;
            this.south = south;
            this.west = west;
            this.top = top;
            this.bottom = bottom;
        }

        private void render(Matrix4f matrix, VertexConsumer consumer, int light) {
            face(matrix, consumer, light, north, 0, 0, -1,
                    x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
            face(matrix, consumer, light, south, 0, 0, 1,
                    x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2);
            face(matrix, consumer, light, west, -1, 0, 0,
                    x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1);
            face(matrix, consumer, light, east, 1, 0, 0,
                    x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
            face(matrix, consumer, light, bottom, 0, -1, 0,
                    x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2);
            face(matrix, consumer, light, top, 0, 1, 0,
                    x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        }

        private static void face(Matrix4f matrix, VertexConsumer consumer, int light, Uv uv,
                                 float nx, float ny, float nz,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float x3, float y3, float z3, float x4, float y4, float z4) {
            vertex(matrix, consumer, light, x1, y1, z1, uv.u1, uv.v1, nx, ny, nz);
            vertex(matrix, consumer, light, x2, y2, z2, uv.u1, uv.v2, nx, ny, nz);
            vertex(matrix, consumer, light, x3, y3, z3, uv.u2, uv.v2, nx, ny, nz);
            vertex(matrix, consumer, light, x4, y4, z4, uv.u2, uv.v1, nx, ny, nz);
        }

        private static void vertex(Matrix4f matrix, VertexConsumer consumer, int light,
                                   float x, float y, float z, float u, float v,
                                   float nx, float ny, float nz) {
            consumer.vertex(matrix, x, y, z).color(255, 255, 255, 255).uv(u, v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(nx, ny, nz).endVertex();
        }
    }
}
'''
out.write_text(source % ("\n".join(cubes) + "\n"), encoding="utf-8")
print(f"generated {len(cubes)} cubes")
