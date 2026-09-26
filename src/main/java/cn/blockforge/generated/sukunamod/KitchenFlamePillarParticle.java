package cn.blockforge.generated.sukunamod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 灶·开爆炸的火柱粒子：从柱底以极快的速度冲天而起，全程保持 0 透明度（完全不透明），
 * 抵达最高点后才用 0.2 秒（4 tick）半透明直至消失。
 * 生成坐标视为火焰底部中心，绘制时整体上移半个身位。
 */
public final class KitchenFlamePillarParticle extends TextureSheetParticle {
    /** 方块半宽：1.0 → 整朵火焰占 2×2 方块。 */
    private static final float BLOCK_HALF_SIZE = 1.0F;
    /** 目标高度：约 88 格。 */
    private static final double RISE_TARGET = 88.0D;
    /** 到顶后的淡出时长：0.2 秒。 */
    private static final int FADE_TICKS = 4;

    private final SpriteSet sprites;

    private KitchenFlamePillarParticle(ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y + BLOCK_HALF_SIZE, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.quadSize = BLOCK_HALF_SIZE * (0.9F + this.random.nextFloat() * 0.3F);
        this.hasPhysics = false;
        this.friction = 1.0F; // 匀速冲天，不衰减
        this.gravity = 0.0F;
        // 冲天速度 30～52 格/秒；寿命 = 升满时间 + 淡出时间，正好在最高点开始淡出。
        double speed = 1.5D + this.random.nextDouble() * 1.1D;
        double riseTicks = RISE_TARGET * (0.9D + this.random.nextDouble() * 0.2D) / speed;
        this.yd = speed;
        // 水平方向只留一点抖动，火柱保持细长。
        this.xd = xd + (this.random.nextDouble() - 0.5D) * 0.04D;
        this.zd = zd + (this.random.nextDouble() - 0.5D) * 0.04D;
        this.lifetime = (int) riseTicks + FADE_TICKS;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);
        // 全程 0 透明度；只有到最高点后的最后 4 tick 才半透明直至消失。
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
            return new KitchenFlamePillarParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
