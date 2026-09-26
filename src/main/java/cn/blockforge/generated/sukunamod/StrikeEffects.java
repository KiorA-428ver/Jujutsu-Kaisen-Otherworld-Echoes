package cn.blockforge.generated.sukunamod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 体术技能（连打/追击/重击）共用的表现层：原版"拳击生物"音效、
 * 打击帧动画粒子与打在目标身上的命中帧动画。
 */
public final class StrikeEffects {
    private StrikeEffects() { }

    /**
     * 让施放者挥动手臂。服务端调用会向周围玩家广播挥臂动画，但看不到
     * 施放者自己的第一人称手臂——那一半由客户端本地 swing 负责（见
     * SukunaClient：连打逐刻挥、追击/重击出手瞬间挥一次）。
     * LivingEntity.swing 自带节奏保护，连打高频调用也只会约每半程重挥一次。
     */
    public static void swingArm(Player player) {
        player.swing(InteractionHand.MAIN_HAND);
    }

    /** 玩家徒手打中生物的原版音效（entity.player.attack.weak），音高轻微随机。 */
    public static void playPunch(ServerLevel level, Vec3 pos) {
        playPunch(level, pos, false);
    }

    /** strong=true 时用重拳版音效（entity.player.attack.strong），供重击单独发声。 */
    public static void playPunch(ServerLevel level, Vec3 pos, boolean strong) {
        level.playSound(null, pos.x, pos.y, pos.z,
                strong ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
                SoundSource.PLAYERS, 1.0F, 0.85F + level.random.nextFloat() * 0.3F);
    }

    /** 在指定位置播放一帧 0.15 秒的打击帧动画；big 为重击的放大两倍版。 */
    public static void spawnStrikeFrame(ServerLevel level, Vec3 pos, boolean big) {
        level.sendParticles((big ? GeneratedMod.STRIKE_FRAME_BIG : GeneratedMod.STRIKE_FRAME).get(),
                pos.x, pos.y, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /** 在给定 AABB（拳区）内随机取一点播放打击帧动画，频率由调用方按伤害脉冲驱动。 */
    public static void spawnStrikeFrameInArea(ServerLevel level, AABB area) {
        RandomSource random = level.random;
        Vec3 pos = new Vec3(
                Mth.lerp(random.nextDouble(), area.minX, area.maxX),
                Mth.lerp(random.nextDouble(), area.minY, area.maxY),
                Mth.lerp(random.nextDouble(), area.minZ, area.maxZ));
        spawnStrikeFrame(level, pos, false);
    }

    /** 命中帧动画：随机出现在受击实体包围盒内的任意一处。 */
    public static void spawnHitImpact(ServerLevel level, LivingEntity target) {
        AABB box = target.getBoundingBox();
        RandomSource random = level.random;
        double inset = 0.12D;
        double u = inset + random.nextDouble() * (1.0D - inset * 2.0D);
        double v = inset + random.nextDouble() * (1.0D - inset * 2.0D);
        double w = inset + random.nextDouble() * (1.0D - inset * 2.0D);
        Vec3 pos = new Vec3(
                Mth.lerp(u, box.minX, box.maxX),
                Mth.lerp(v, box.minY, box.maxY),
                Mth.lerp(w, box.minZ, box.maxZ));
        level.sendParticles(GeneratedMod.STRIKE_HIT_FRAME.get(),
                pos.x, pos.y, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }
}
