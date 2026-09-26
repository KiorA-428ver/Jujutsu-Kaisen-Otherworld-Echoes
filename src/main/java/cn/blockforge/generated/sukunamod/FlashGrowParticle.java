package cn.blockforge.generated.sukunamod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 灶·开第五波闪光：0.2 秒（4 tick）内从小平滑放大到最大，然后直接消失。
 * 出现在第四发闪光的 0.2 秒后；放大到最大的瞬间由服务端推送截屏冲击帧。
 */
public final class FlashGrowParticle extends TextureSheetParticle {
    /** 放大时长：0.2 秒（4 tick）。 */
    private static final int GROW_TICKS = 4;
    /** 起始方块半宽（随最大尺寸同步缩小为原来的 30%）。 */
    private static final float START_HALF = 0.3F;
    /** 最大方块半宽：直径约 18 格（原 60 格的 30%），仍比前四发的收尾尺寸更大。 */
    private static final float MAX_HALF = 9.0F;

    private final SpriteSet sprites;

    private FlashGrowParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.quadSize = START_HALF;
        this.hasPhysics = false;
        this.lifetime = GROW_TICKS;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);
        // t：0 → 1；smoothstep 让尺寸从小平滑地加速再减速放大到最大，最后一 tick 直接消失。
        float t = Math.min(1.0F, (float) (this.age + 1) / (float) GROW_TICKS);
        float smooth = t * t * (3.0F - 2.0F * t);
        this.quadSize = START_HALF + (MAX_HALF - START_HALF) * smooth;
        this.alpha = 1.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_LIT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new FlashGrowParticle(level, x, y, z, sprites);
        }
    }
}
