package cn.blockforge.generated.sukunamod;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 打击 / 打击命中帧动画粒子：0.15 秒（3 tick）内按顺序播完上传的帧贴图后消失。
 * 帧号用 (age + partialTick) 按渲染帧插值——4 帧塞进 3 tick 时每帧也能均分到
 * 约 0.75 tick；PARTICLE_SHEET_LIT 满亮度渲染，贴图是黑底转透明的白色线稿，
 * 画面上就是一圈发光的白色冲击环。
 */
public final class StrikeFrameParticle extends TextureSheetParticle {
    /** 一轮帧动画的时长：0.15 秒（3 tick）。 */
    private static final int LIFETIME_TICKS = 3;
    /** 最后 30% 时长淡出，收尾帧（碎裂圆环）不会生硬跳没。 */
    private static final float FADE_START = 0.7F;

    private final SpriteSet sprites;
    /** 帧数：打击 4 帧、命中 3 帧（1.20.1 的 SpriteSet 没有 size()，由 Provider 显式传入）。 */
    private final int frames;

    private StrikeFrameParticle(ClientLevel level, double x, double y, double z,
                                SpriteSet sprites, int frames, float size) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.frames = frames;
        this.quadSize = size;
        this.lifetime = LIFETIME_TICKS;
        this.hasPhysics = false;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTick) {
        // 1.20.1 的 SpriteSet.get(a, b) 取 sprites[a * (实际帧数-1) / b]，是进度映射：
        // 传 (index, frames-1) 才正好取到第 index 帧。t 按渲染帧插值，
        // 4 帧塞进 3 tick 时每帧均分约 0.75 tick。
        float t = Math.min(1.0F, (this.age + partialTick) / LIFETIME_TICKS);
        int index = (int) (t * (this.frames - 1));
        this.sprite = this.sprites.get(index, this.frames - 1);
        this.alpha = t < FADE_START ? 1.0F : Math.max(0.0F, (1.0F - t) / (1.0F - FADE_START));
        super.render(consumer, camera, partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_LIT;
    }

    /** 连打/追击在拳区随机播放的打击帧：直径约 0.8 格。 */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new StrikeFrameParticle(level, x, y, z, sprites, 4, 0.8F);
        }
    }

    /** 重击在攻击范围中心播放的打击帧：放大两倍。 */
    public static final class BigProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public BigProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new StrikeFrameParticle(level, x, y, z, sprites, 4, 1.6F);
        }
    }

    /** 打击命中帧（圆环+X 准星）：贴在受击实体身上，直径约 0.7 格。 */
    public static final class HitProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public HitProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new StrikeFrameParticle(level, x, y, z, sprites, 3, 0.7F);
        }
    }
}
