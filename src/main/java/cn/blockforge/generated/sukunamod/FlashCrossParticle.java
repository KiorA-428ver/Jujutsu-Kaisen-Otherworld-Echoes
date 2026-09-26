package cn.blockforge.generated.sukunamod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 灶·开起爆前出现的十字闪光：0.15 秒（3 tick）内先放大、再缩小、随后消失。
 * 一次引信里按 0.3 / 0.4 / 0.8 / 0.9 秒先后闪出四枚，两种贴图交替使用。
 */
public final class FlashCrossParticle extends TextureSheetParticle {
    /** 闪光时长：0.15 秒（3 tick）。 */
    private static final int FLASH_TICKS = 3;
    /** 放大到最大尺寸的时间占比：前 55%。 */
    private static final float GROW_RATIO = 0.55F;
    /** 起始方块半宽（随最大尺寸同步缩小为原来的 30%）。 */
    private static final float START_HALF = 0.36F;

    private final SpriteSet sprites;
    private final float maxHalf;

    private FlashCrossParticle(ClientLevel level, double x, double y, double z,
                               SpriteSet sprites, float maxHalf) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.maxHalf = maxHalf;
        this.quadSize = START_HALF;
        this.hasPhysics = false;
        this.lifetime = FLASH_TICKS;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);
        // t：0 → 1；尺寸前 55% 缓出放大到最大，之后线性缩回，最后阶段淡出消失。
        float t = Math.min(1.0F, (float) (this.age + 1) / (float) FLASH_TICKS);
        float grow;
        if (t < GROW_RATIO) {
            float g = t / GROW_RATIO;
            grow = 1.0F - (1.0F - g) * (1.0F - g) * (1.0F - g);
        } else {
            grow = Math.max(0.0F, 1.0F - (t - GROW_RATIO) / (1.0F - GROW_RATIO));
        }
        this.quadSize = START_HALF + (this.maxHalf - START_HALF) * grow;
        this.alpha = t < 0.75F ? 1.0F : Math.max(0.0F, (1.0F - t) / 0.25F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_LIT;
    }

    /** 第一发：细长十字，最大直径约 9 格（原 30 格的 30%）。 */
    public static final class Provider1 implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider1(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new FlashCrossParticle(level, x, y, z, sprites, 4.5F);
        }
    }

    /** 第二发：更亮的四角星，最大直径约 12 格（原 40 格的 30%）。 */
    public static final class Provider2 implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider2(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new FlashCrossParticle(level, x, y, z, sprites, 6.0F);
        }
    }
}
