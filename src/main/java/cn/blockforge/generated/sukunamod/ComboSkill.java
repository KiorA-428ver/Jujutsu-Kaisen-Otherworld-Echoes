package cn.blockforge.generated.sukunamod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** 连打：按住数字 3 时，在玩家面前 3x3x3 范围内持续进行短距离连击。 */
public final class ComboSkill {
    private static final double PULSE_INTERVAL_TICKS = 1.6D; // 0.08 秒
    private static final double HITBOX_SIZE = 3.0D; // 攻击范围：面前 3x3x3
    private static final double HITBOX_DISTANCE = 1.5D;
    private static final double DAMAGE_MULTIPLIER = 0.5D;
    private static final long INPUT_TIMEOUT_TICKS = 2L;
    private static final long MAX_HOLD_TICKS = 60L; // 连打长按上限：3 秒
    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private ComboSkill() { }

    /** 客户端按住数字 3 时每刻发送一次；服务端据此维持连打。 */
    public static void hold(Player player) {
        if (!(player.level() instanceof ServerLevel server)
                || !CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)
                || !player.isAlive()) return;

        long now = server.getGameTime();
        State state = ACTIVE.get(player.getUUID());
        if (state == null || state.level != server || state.lastInputTick < now - INPUT_TIMEOUT_TICKS) {
            if (!SkillCooldowns.tryUse(player, SkillCooldowns.COMBO,
                    SkillCooldowns.COMBO_COOLDOWN_TICKS)) return;
            state = new State(server, now);
            ACTIVE.put(player.getUUID(), state);
            pulse(server, player);
            return;
        }
        if (state.lastInputTick == now || now - state.startedTick >= MAX_HOLD_TICKS) {
            if (now - state.startedTick >= MAX_HOLD_TICKS) ACTIVE.remove(player.getUUID());
            return;
        }
        state.lastInputTick = now;
        state.progress += 1.0D;
        while (state.progress >= PULSE_INTERVAL_TICKS) {
            state.progress -= PULSE_INTERVAL_TICKS;
            pulse(server, player);
        }
    }

    /** 清理松开按键、死亡或换维度后的状态。 */
    public static void tick(Player player) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) return;
        long now = player.level().getGameTime();
        if (!player.isAlive() || state.level != player.level()
                || now - state.lastInputTick > INPUT_TIMEOUT_TICKS) {
            ACTIVE.remove(player.getUUID());
        }
        // 防止异常断线留下永远不会再访问的 UUID 状态。
        if (ACTIVE.size() > 256) {
            Iterator<Map.Entry<UUID, State>> iterator = ACTIVE.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, State> entry = iterator.next();
                if (now - entry.getValue().lastInputTick > INPUT_TIMEOUT_TICKS) iterator.remove();
            }
        }
    }

    private static void pulse(ServerLevel server, Player player) {
        Vec3 direction = player.getLookAngle().normalize();
        if (direction.lengthSqr() < 1.0E-8D) return;
        Vec3 center = player.getEyePosition().add(direction.scale(HITBOX_DISTANCE));
        AABB area = new AABB(center.x - HITBOX_SIZE * 0.5D, center.y - HITBOX_SIZE * 0.5D,
                center.z - HITBOX_SIZE * 0.5D, center.x + HITBOX_SIZE * 0.5D,
                center.y + HITBOX_SIZE * 0.5D, center.z + HITBOX_SIZE * 0.5D);

        // 每次伤害脉冲在 3x3x3 拳区内随机位置播一帧 0.15 秒的打击帧动画，
        // 并配一记原版"拳击生物"音效——播放频率与造成伤害的频率相当。
        // 同时挥动手臂（服务端这半：让周围玩家也看到；第一人称由客户端本地挥）。
        StrikeEffects.swingArm(player);
        StrikeEffects.spawnStrikeFrameInArea(server, area);
        StrikeEffects.playPunch(server, center);

        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && entity.isAlive())) {
            damageTarget(server, player, target);
        }
    }

    private static void damageTarget(ServerLevel server, Player player, LivingEntity target) {
        ItemStack held = player.getMainHandItem();
        double heldDamage = Math.max(2.0D, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float damage = (float) (heldDamage * DAMAGE_MULTIPLIER * JjkoaConfig.DAMAGE_MULTIPLIER);
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        if (!target.hurt(server.damageSources().playerAttack(player), damage)) return;
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        int fire = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, held);
        if (fire > 0) target.setSecondsOnFire(fire * 4);

        // 命中反馈：爆炸粒子与烟花音效已移除，改为贴在目标身上随机位置的打击命中帧动画。
        StrikeEffects.spawnHitImpact(server, target);
    }

    private static final class State {
        private final ServerLevel level;
        private final long startedTick;
        private long lastInputTick;
        private double progress;

        private State(ServerLevel level, long lastInputTick) {
            this.level = level;
            this.startedTick = lastInputTick;
            this.lastInputTick = lastInputTick;
        }
    }
}
