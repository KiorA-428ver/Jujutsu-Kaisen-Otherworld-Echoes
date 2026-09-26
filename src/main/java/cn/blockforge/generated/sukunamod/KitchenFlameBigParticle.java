package cn.blockforge.generated.sukunamod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 灶·开爆炸的底盘 / 心火粒子：固定占据 2×2 方块的大型火焰。
 * 1.20.1 中粒子四边形以自身为中心、半宽恰等于 quadSize（单位：方块），
 * 因此 quadSize=1.0 即渲染为 2×2 方块；生成坐标视为火焰底部中心，绘制时整体上移半个身位。
 * 存活期间保持 0 透明度（完全不透明），临终前 0.15 秒（3 tick）半透明直至消失。
 */
public final class KitchenFlameBigParticle extends TextureSheetParticle {
    /** 方块半宽：1.0 → 整朵火焰占 2×2 方块。 */
    public static final float BLOCK_HALF_SIZE = 1.0F;
    /** 消失前的淡出时长：0.15 秒。 */
    private static final int FADE_TICKS = 3;

    private final SpriteSet sprites;

    private KitchenFlameBigParticle(ClientLevel level, double x, double y, double z,
                                    double xd, double yd, double zd, SpriteSet sprites) {
        // 包里的速度分量是 nextGaussian()*speed，量级很小，这里只做轻微飘移。
        super(level, x, y + BLOCK_HALF_SIZE, z, xd * 0.6D, 0.0D, zd * 0.6D);
        this.sprites = sprites;
        this.quadSize = BLOCK_HALF_SIZE * (0.9F + this.random.nextFloat() * 0.25F);
        this.hasPhysics = false;
        this.friction = 0.98F;
        this.gravity = 0.0F;
        // 缓缓往上舔的火苗速度。
        this.yd = 0.08D + this.random.nextDouble() * 0.18D;
        this.lifetime = 26 + this.random.nextInt(14);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);
        // 全程 0 透明度；最后 3 tick 半透明直到消失。
        int fadeStart = this.lifetime - FADE_TICKS;
        this.alpha = this.age < fadeStart
                ? 1.0F
                : Math.max(0.0F, (float) (this.lifetime - this.age) / (float) FADE_TICKS);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
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
            return new KitchenFlameBigParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
