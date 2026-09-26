package cn.blockforge.generated.sukunamod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** 灶·开准备使用的火焰粒子。 */
public final class KitchenFlameParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    private KitchenFlameParticle(ClientLevel level, double x, double y, double z,
                                 double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.sprites = sprites;
        this.friction = 0.90F;
        this.gravity = -0.015F;
        this.hasPhysics = false;
        // 火焰场用 xd 传入 1.0–3.0 的随机尺寸；蓄力和爆发粒子仍使用小型默认尺寸。
        this.quadSize = xd >= 1.0D && xd <= 3.0D
                ? (float) xd : 0.18F + random.nextFloat() * 0.08F;
        // xd 只作为尺寸通道，不让火焰粒子被尺寸值推飞。
        if (xd >= 1.0D && xd <= 3.0D) this.xd = this.yd = this.zd = 0.0D;
        this.lifetime = 8 + random.nextInt(6);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);
        this.alpha = Math.max(0.0F, 1.0F - (float) age / lifetime);
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
            return new KitchenFlameParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
