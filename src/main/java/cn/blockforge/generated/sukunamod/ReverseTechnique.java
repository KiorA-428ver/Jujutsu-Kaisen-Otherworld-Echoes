package cn.blockforge.generated.sukunamod;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** 反转术式：按住 V 时在右臂燃起白色火焰，并持续治疗。 */
public final class ReverseTechnique {
    private static final long INPUT_TIMEOUT_TICKS = 3L;
    // 服务端以 tick 为最小调度单位；0.01 秒小于一个游戏 tick，因此每 tick 恢复一次。
    private static final int HEAL_INTERVAL_TICKS = 1;
    private static final float HEAL_FRACTION = 0.03F;
    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private ReverseTechnique() { }

    public static void hold(Player player) {
        if (!(player.level() instanceof ServerLevel server)
                || !CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)
                || !player.isAlive()) return;
        long now = server.getGameTime();
        if (!SkillCooldowns.isReady(player, SkillCooldowns.REVERSE)) return;
        if (!CursedEnergy.tryConsumeTimed(player, "reverse", 50, 2)) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        State state = ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new State(now));
        state.lastInputTick = now;
        if (state.lastHealTick == Long.MIN_VALUE || now - state.lastHealTick >= HEAL_INTERVAL_TICKS) {
            healAndDisplay(server, player);
            state.lastHealTick = now;
        }
    }

    public static void tick(Player player) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) return;
        long now = player.level().getGameTime();
        if (!player.isAlive() || !CombatMode.isServerActive(player)
                || !CombatMode.isUnlocked(player)
                || now - state.lastInputTick > INPUT_TIMEOUT_TICKS) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        if (now - state.lastHealTick >= HEAL_INTERVAL_TICKS
                && player.level() instanceof ServerLevel server) {
            healAndDisplay(server, player);
            state.lastHealTick = now;
        }
        if (ACTIVE.size() > 256) {
            Iterator<Map.Entry<UUID, State>> iterator = ACTIVE.entrySet().iterator();
            while (iterator.hasNext()
                    && now - iterator.next().getValue().lastInputTick > INPUT_TIMEOUT_TICKS) iterator.remove();
        }
    }

    private static void healAndDisplay(ServerLevel server, Player player) {
        player.heal(player.getMaxHealth() * HEAL_FRACTION);
        // Minecraft 的右臂位于玩家自身右侧：由水平朝向向量计算，而不是把粒子固定在世界 X 轴。
        float yaw = player.getYRot() * ((float) Math.PI / 180.0F);
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw)).normalize();
        Vec3 arm = player.position().add(right.scale(0.36D))
                .add(0.0D, player.getBbHeight() * 0.63D, 0.0D);
        SimpleParticleType particle = GeneratedMod.REVERSE_FLAME.get();
        server.sendParticles(particle, arm.x, arm.y, arm.z, 8,
                0.12D, 0.22D, 0.12D, 0.02D);
    }

    private static final class State {
        private long lastInputTick;
        private long lastHealTick = Long.MIN_VALUE;

        private State(long lastInputTick) {
            this.lastInputTick = lastInputTick;
        }
    }
}
