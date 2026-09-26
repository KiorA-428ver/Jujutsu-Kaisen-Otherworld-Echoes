package cn.blockforge.generated.sukunamod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * 灶·开爆炸的粒子编排（服务端逐 tick 驱动，形如一颗倒扣的螺丝钉），全程持续 8 秒：
 *  1. 圆柱火焰——大火焰（2×2）贴着半径 12 格的柱底源源不断地冲天而起（约 30～52 格/秒），
 *     到最高点前保持 0 透明度，抵达最高点后用 0.2 秒半透明直至消失；
 *  2. 底盘——大火焰从中心开始以约 1.5 秒一圈的速度反复向外铺，8 秒内不断循环，
 *     铺到最外围后同样保持不透明、临终 0.15 秒淡出；底盘高 3～4 格；
 *  3. 小火焰粒子贴着圆柱表面与底盘火面飘荡。
 * 大火焰全程保持完全不透明，只在生命尽头淡出，因此画面浓密不稀疏。
 */
final class FlameExplosion {
    /** 圆柱：底部半径 12 格、高 88 格。 */
    private static final double COLUMN_RADIUS = 12.0D;
    /** 底盘：半径 26 格、高 3～4 格。 */
    private static final double BASE_RADIUS = 26.0D;
    private static final double BASE_HEIGHT = 3.5D;
    /** 整个爆炸编排时长：160 tick（8 秒），火焰不断生成、保持 8 秒。 */
    private static final int TOTAL_TICKS = 160;
    /** 底盘扫一圈的周期：30 tick（1.5 秒）从中心推到半径 26 格，8 秒内反复循环。 */
    private static final int SWEEP_PERIOD = 30;

    private final ServerLevel server;
    private final Vec3 center;
    private final Random random;
    private int elapsed;

    FlameExplosion(ServerLevel server, Vec3 center) {
        this.server = server;
        this.center = center;
        this.random = new Random(server.getRandom().nextLong());
    }

    ServerLevel level() {
        return server;
    }

    /** 推进一帧并播撒本帧粒子；返回 false 表示编排结束。 */
    boolean tick() {
        elapsed++;
        if (elapsed > TOTAL_TICKS) return false;
        emitPillar();
        emitBase();
        emitAmbient();
        return true;
    }

    /**
     * 圆柱：每 tick 从柱底整片半径 12 的圆盘生成 40 朵冲天大火焰，
     * 粒子自行以 30～52 格/秒拔高、到最高点淡出，8 秒内火头源源不断。
     */
    private void emitPillar() {
        for (int i = 0; i < 40; i++) {
            Vec3 p = diskPoint(COLUMN_RADIUS);
            spawnPillar(center.x + p.x, center.y + random.nextDouble() * 1.2D, center.z + p.z);
        }
    }

    /**
     * 底盘：一个约 1.5 秒一圈的扫掠火头从中心高速推到半径 26 格并循环 8 秒，
     * 每 tick 沿推进环带生成 22 朵大火焰；已铺区域同时补燃 18 朵，保持整盘火面浓密。
     */
    private void emitBase() {
        double phase = (elapsed % SWEEP_PERIOD) / (double) SWEEP_PERIOD;
        double front = BASE_RADIUS * phase;
        for (int i = 0; i < 22; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = Math.max(0.0D, front + (random.nextDouble() - 0.5D) * 2.4D);
            spawnLarge(center.x + Math.cos(angle) * radius,
                    center.y + random.nextDouble() * 1.2D,
                    center.z + Math.sin(angle) * radius);
        }
        for (int i = 0; i < 18; i++) {
            Vec3 p = diskPoint(BASE_RADIUS);
            spawnLarge(center.x + p.x, center.y + random.nextDouble() * BASE_HEIGHT,
                    center.z + p.z);
        }
    }

    /** 飘荡氛围：小火焰贴着圆柱外壁、柱心与底盘火面游动。 */
    private void emitAmbient() {
        for (int i = 0; i < 24; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = COLUMN_RADIUS * (1.0D + random.nextDouble() * 0.45D);
            spawnSmall(center.x + Math.cos(angle) * radius,
                    center.y + random.nextDouble() * 88.0D,
                    center.z + Math.sin(angle) * radius, 0.08D);
        }
        for (int i = 0; i < 12; i++) {
            Vec3 p = diskPoint(COLUMN_RADIUS);
            spawnSmall(center.x + p.x, center.y + random.nextDouble() * 88.0D,
                    center.z + p.z, 0.1D);
        }
        for (int i = 0; i < 16; i++) {
            Vec3 p = diskPoint(BASE_RADIUS);
            spawnSmall(center.x + p.x, center.y + random.nextDouble() * 3.0D,
                    center.z + p.z, 0.12D);
        }
    }

    private void spawnPillar(double x, double y, double z) {
        server.sendParticles(GeneratedMod.KITCHEN_FLAME_PILLAR.get(), x, y, z,
                1, 0.1D, 0.0D, 0.1D, 0.004D);
    }

    private void spawnLarge(double x, double y, double z) {
        server.sendParticles(GeneratedMod.KITCHEN_FLAME_2X2.get(), x, y, z,
                1, 0.12D, 0.12D, 0.12D, 0.004D);
    }

    private void spawnSmall(double x, double y, double z, double speed) {
        server.sendParticles(GeneratedMod.KITCHEN_FLAME.get(), x, y, z,
                1, 0.0D, 0.0D, 0.0D, speed);
    }

    /** 圆盘内均匀取点（半径按平方根分布，避免圆心过密）。 */
    private Vec3 diskPoint(double radius) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double r = radius * Math.sqrt(random.nextDouble());
        return new Vec3(Math.cos(angle) * r, 0.0D, Math.sin(angle) * r);
    }
}
