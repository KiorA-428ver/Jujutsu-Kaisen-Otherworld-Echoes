package cn.blockforge.generated.sukunamod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** 技能 2：世界斩的服务端蓄力、咒词与发射逻辑。 */
public final class WorldSlashSkill {
    public static final int CHARGE_TICKS = 30; // 1.5 秒
    private static final int CHANT_INTERVAL_TICKS = 10;
    private static final String CHARGE_TAG = "sukunamod_world_slash_charge";
    private static final String LAST_CHANT_TAG = "sukunamod_world_slash_last_chant";
    private static final String CHANT_INDEX_TAG = "sukunamod_world_slash_chant_index";
    private static final String[] CHANTS = {"龙鳞", "反发", "成双之流星"};
    private static final int ENERGY_COST = 6_800;
    /** 脱手瞬间的第一帧黑白冲击帧：黑白反色，持续 0.15 秒。 */
    private static final int IMPACT_INVERTED_MS = 150;
    /** 紧接着的第二帧黑白冲击帧：正常黑白，持续 0.2 秒（两帧合计 0.35 秒）。 */
    private static final int IMPACT_NORMAL_MS = 200;
    /** 每出现一帧冲击画面震一下屏幕：0.2 秒、中度振幅（175%，与重击同档）。 */
    private static final int IMPACT_SHAKE_TICKS = 4;
    private static final int IMPACT_SHAKE_PERCENT = 175;
    /** 冲击帧的覆盖半径：施放者与周围这个范围内的玩家都会截屏看到两帧。 */
    private static final double IMPACT_RADIUS = 32.0D;

    private WorldSlashSkill() { }

    /** 数字 2 按住期间每 tick 调用。 */
    public static void hold(Player player) {
        if (!CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)) return;
        long now = player.level().getGameTime();
        int charge = player.getPersistentData().getInt(CHARGE_TAG);
        if (charge >= CHARGE_TICKS) return;
        charge++;
        player.getPersistentData().putInt(CHARGE_TAG, charge);
        long lastChant = player.getPersistentData().getLong(LAST_CHANT_TAG);
        if (charge == CHANT_INTERVAL_TICKS
                || charge == CHANT_INTERVAL_TICKS * 2
                || charge == CHANT_INTERVAL_TICKS * 3) {
            int index = Math.min(CHANTS.length - 1, charge / CHANT_INTERVAL_TICKS - 1);
            if (!player.getPersistentData().contains(LAST_CHANT_TAG) || now > lastChant) {
                broadcastChant(player, index);
                player.getPersistentData().putLong(LAST_CHANT_TAG, now);
                player.getPersistentData().putInt(CHANT_INDEX_TAG, index);
            }
        }
    }

    /** 松开数字 2 后，只有完整蓄力才发射世界斩。 */
    public static void release(Player player) {
        if (player == null) return;
        int charge = player.getPersistentData().getInt(CHARGE_TAG);
        player.getPersistentData().remove(CHARGE_TAG);
        player.getPersistentData().remove(LAST_CHANT_TAG);
        player.getPersistentData().remove(CHANT_INDEX_TAG);
        if (charge < CHARGE_TICKS || !CombatMode.isServerActive(player)
                || !CombatMode.isUnlocked(player) || !CursedEnergy.tryConsume(player, ENERGY_COST)) return;
        DismantleProjectile slash = new DismantleProjectile(
                GeneratedMod.DISMANTLE_PROJECTILE.get(), player.level());
        slash.setOwner(player);
        slash.setWorldSlash(true);
        slash.setPos(player.getX(), player.getEyeY() - 0.15D, player.getZ());
        slash.launch(player.getLookAngle(), 0.0F);
        player.level().addFreshEntity(slash);
        if (player.level() instanceof ServerLevel server) {
            // 发射世界斩的这一声换成"终末斩"音效，原来的横扫之刃声已按要求删除。
            server.playSound(null, player.blockPosition(), GeneratedMod.WORLD_SLASH_SOUND.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            // 脱手的这一帧立刻截屏播两次黑白冲击帧（0.15 秒反色 + 0.2 秒正常黑白），
            // 每出现一帧震一下屏幕；这 0.35 秒正好盖住斩击在原位滞留的那段。
            notifySlashImpact(server, player);
        }
    }

    /** 向施放者本人以及周围 IMPACT_RADIUS 格内的玩家推送脱手瞬间的黑白冲击帧。 */
    private static void notifySlashImpact(ServerLevel server, Player caster) {
        double radiusSquared = IMPACT_RADIUS * IMPACT_RADIUS;
        for (ServerPlayer target : server.getEntitiesOfClass(ServerPlayer.class,
                caster.getBoundingBox().inflate(IMPACT_RADIUS, IMPACT_RADIUS, IMPACT_RADIUS))) {
            if (target != caster && caster.position().distanceToSqr(target.position()) > radiusSquared) {
                continue;
            }
            JjkoaNetwork.sendSlashImpact(target, IMPACT_SHAKE_TICKS, IMPACT_SHAKE_PERCENT,
                    IMPACT_INVERTED_MS, IMPACT_NORMAL_MS);
        }
    }

    public static void reset(Player player) {
        if (player == null) return;
        player.getPersistentData().remove(CHARGE_TAG);
        player.getPersistentData().remove(LAST_CHANT_TAG);
        player.getPersistentData().remove(CHANT_INDEX_TAG);
    }

    private static void broadcastChant(Player player, int index) {
        if (!(player.level() instanceof ServerLevel server)) return;
        Component message = Component.literal(CHANTS[index]);
        for (ServerPlayer recipient : server.getServer().getPlayerList().getPlayers()) {
            recipient.sendSystemMessage(message);
        }
        float pitch = 0.9F + index * 0.2F;
        server.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.PLAYERS, 1.0F, pitch);
    }
}
