package cn.blockforge.generated.sukunamod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 灶·开：按住数字 7 蓄力，松开后发射火焰箭。蓄力时火矢（双面交叉的 4 帧动画贴图，
 * 见 FlameArrowRenderer）悬停在视角右方、与拉弓时弓箭的位置一致，并带加色外发光。
 * 箭矢触到实体/方块立即消失，1.85 秒引信：0.3 秒起先后闪出四枚十字闪光（每枚 0.15 秒放大缩小消失），
 * 第四发后 0.2 秒出现第五发，用 0.2 秒从小平滑放大到最大后消失；放到最大的瞬间以玩家当前屏幕
 * 截取画面播冲击帧——先 2 帧极致黑白反色，再接持续 0.25 秒的正常黑白，播放期间画面震颤一下；
 * 随后原地爆炸，驱动 8 秒的火柱冲天与底盘铺火编排，并对半径 48 格（直径 96 格）内的玩家造成 5.5 秒屏幕颤动。
 */
public final class FlameArrowSkill {
    private static final long INPUT_TIMEOUT_TICKS = 3L;
    private static final int FIELD_LIFETIME_TICKS = 160; // 伤害场持续 8 秒
    /** 蓄力火矢悬停在视角右方（与拉弓时弓箭的位置一致）：前伸 / 右移 / 下沉距离。 */
    private static final double ARROW_DISTANCE = 1.55D;
    private static final double ARROW_RIGHT_OFFSET = 0.52D;
    private static final double ARROW_HEIGHT_OFFSET = -0.18D;
    private static final double ARROW_DAMAGE_MULTIPLIER = 6.0D;
    private static final double FIELD_RADIUS = 48.0D;
    private static final double DAMAGE_HEIGHT = 30.0D;
    /** 箭矢消失后的延迟爆炸：37 tick（1.85 秒）。 */
    private static final int FUSE_TICKS = 37;
    /** 前四发十字闪光的出现时刻（箭矢消失后起算）：0.3 / 0.4 / 0.8 / 0.9 秒。 */
    private static final int FLASH_1_AT = 6;
    private static final int FLASH_2_AT = 8;
    private static final int FLASH_3_AT = 16;
    private static final int FLASH_4_AT = 18;
    /** 第五波闪光：第四发出现 0.2 秒（4 tick）后放出，0.2 秒内从小平滑放大到最大再消失。 */
    private static final int FLASH_5_AT = FLASH_4_AT + 4;
    /** 第五发放到最大的时刻（出现后 4 tick）：截屏起播冲击帧。 */
    private static final int FLASH_5_MAX_AT = FLASH_5_AT + 4;
    /** 爆炸时屏幕颤动时长：110 tick（5.5 秒）。 */
    private static final int SHAKE_TICKS = 110;
    /** 冲击帧触发标记：大于 0 即让客户端截屏播"2 帧反色 + 0.25 秒正常黑白"，具体节奏由客户端掌握。 */
    private static final int IMPACT_TRIGGER = 1;
    private static final Map<UUID, ChargeState> CHARGING = new HashMap<>();
    private static final Map<UUID, List<PendingImpact>> PENDING = new HashMap<>();
    private static final Map<UUID, List<FlameField>> FIELDS = new HashMap<>();
    /** 爆炸编排（火柱冲天 + 底盘辐射喷射）。 */
    private static final Map<UUID, List<FlameExplosion>> EXPLOSIONS = new HashMap<>();

    private FlameArrowSkill() { }

    /** 客户端按住 7 时每 tick 发送一次。 */
    public static void hold(Player player) {
        if (!(player.level() instanceof ServerLevel server)
                || !CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)
                || !player.isAlive()) return;

        long now = server.getGameTime();
        ChargeState state = CHARGING.get(player.getUUID());
        if (state == null || state.level != server || state.lastInputTick < now - INPUT_TIMEOUT_TICKS) {
            if (!SkillCooldowns.isReady(player, SkillCooldowns.FLAME_ARROW)) return;
            if (!CursedEnergy.has(player, 1200)) return;
            if (state != null) removeArrow(state);
            FlameArrowEntity arrow = new FlameArrowEntity(GeneratedMod.FLAME_ARROW_ENTITY.get(), server);
            arrow.setOwner(player);
            server.addFreshEntity(arrow);
            state = new ChargeState(server, arrow, now);
            CHARGING.put(player.getUUID(), state);
        }
        state.lastInputTick = now;
        aimArrow(player, state.arrow);
        emitChargingParticles(server, state.arrow.position());
    }

    /** 松开 7 后发射当前蓄力的火焰箭。 */
    public static void release(Player player) {
        ChargeState state = CHARGING.remove(player.getUUID());
        if (state == null || state.level != player.level() || !state.arrow.isAlive()) return;
        if (!(player.level() instanceof ServerLevel server)
                || !CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)
                || !player.isAlive()) {
            removeArrow(state);
            return;
        }
        Vec3 direction = player.getLookAngle().normalize();
        if (direction.lengthSqr() < 1.0E-8D
                || !CursedEnergy.has(player, 1200)
                || !SkillCooldowns.tryUse(player, SkillCooldowns.FLAME_ARROW,
                SkillCooldowns.FLAME_ARROW_COOLDOWN_TICKS)
                || !CursedEnergy.tryConsume(player, 1200)) {
            removeArrow(state);
            return;
        }
        aimArrow(player, state.arrow);
        state.arrow.launch(direction);
        server.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F,
                0.85F + server.random.nextFloat() * 0.2F);
    }

    /** 箭矢触到实体/方块：原地消失并排 1.85 秒引信；五发十字闪光与冲击帧按引信进度出现。 */
    public static void scheduleImpact(ServerLevel server, FlameArrowEntity arrow) {
        Vec3 center = arrow.position();
        Player owner = arrow.getOwner() instanceof Player player ? player : null;
        arrow.discard();
        if (owner == null) return;
        PENDING.computeIfAbsent(owner.getUUID(), ignored -> new ArrayList<>())
                .add(new PendingImpact(server, center, owner));
    }

    /** 每 tick 清理蓄力超时状态，推进引信、爆炸编排与该玩家的伤害场。 */
    public static void tick(Player player) {
        UUID id = player.getUUID();
        ChargeState state = CHARGING.get(id);
        long now = player.level().getGameTime();
        if (state != null && (state.level != player.level() || !player.isAlive()
                || !CombatMode.isServerActive(player)
                || now - state.lastInputTick > INPUT_TIMEOUT_TICKS)) {
            removeArrow(state);
            CHARGING.remove(id);
        }

        List<PendingImpact> pending = PENDING.get(id);
        if (pending != null) {
            Iterator<PendingImpact> iterator = pending.iterator();
            while (iterator.hasNext()) {
                PendingImpact impact = iterator.next();
                if (impact.level != player.level()) {
                    iterator.remove();
                    continue;
                }
                impact.ticksLeft--;
                // 第 1、2 发相隔 0.1 秒；第 2 发消失后停 0.25 秒出第 3 发；第 3、4 发再相隔 0.1 秒；
                // 第 4 发出现 0.2 秒后放出第五发（0.2 秒从小放大到最大再消失）。
                int elapsed = FUSE_TICKS - impact.ticksLeft;
                if (elapsed == FLASH_1_AT || elapsed == FLASH_3_AT) {
                    spawnFlash(impact.level, impact.center, GeneratedMod.FLASH_CROSS_1.get());
                } else if (elapsed == FLASH_2_AT || elapsed == FLASH_4_AT) {
                    spawnFlash(impact.level, impact.center, GeneratedMod.FLASH_CROSS_2.get());
                } else if (elapsed == FLASH_5_AT) {
                    spawnFlash(impact.level, impact.center, GeneratedMod.FLASH_CROSS_5.get());
                }
                if (elapsed == FLASH_5_MAX_AT) {
                    // 第五发放到最大的瞬间：直径 96 格内的玩家截取当前屏幕，开播冲击帧（含画面震颤）。
                    notifyBurst(impact.level, impact.center, 0, IMPACT_TRIGGER);
                }
                if (impact.ticksLeft % 3 == 0) {
                    // 引信期间落点冒火星，提示即将起爆。
                    impact.level.sendParticles(GeneratedMod.KITCHEN_FLAME.get(),
                            impact.center.x, impact.center.y + 0.3D, impact.center.z,
                            3, 0.3D, 0.2D, 0.3D, 0.06D);
                }
                if (impact.ticksLeft <= 0) {
                    iterator.remove();
                    explode(impact.level, impact.owner, impact.center);
                }
            }
            if (pending.isEmpty()) PENDING.remove(id);
        }

        List<FlameExplosion> explosions = EXPLOSIONS.get(id);
        if (explosions != null) {
            Iterator<FlameExplosion> explosionIterator = explosions.iterator();
            while (explosionIterator.hasNext()) {
                FlameExplosion explosion = explosionIterator.next();
                if (explosion.level() != player.level() || !explosion.tick()) {
                    explosionIterator.remove();
                }
            }
            if (explosions.isEmpty()) EXPLOSIONS.remove(id);
        }

        List<FlameField> fields = FIELDS.get(id);
        if (fields == null) return;
        Iterator<FlameField> iterator = fields.iterator();
        while (iterator.hasNext()) {
            FlameField field = iterator.next();
            if (field.level != player.level() || now >= field.endTick) {
                iterator.remove();
                continue;
            }
            damageField(field, player);
        }
        if (fields.isEmpty()) FIELDS.remove(id);
    }

    /** 引信烧尽后起爆：攻击场、爆炸编排、颤动通知。 */
    private static void explode(ServerLevel server, Player owner, Vec3 center) {
        if (owner != null) {
            FIELDS.computeIfAbsent(owner.getUUID(), ignored -> new ArrayList<>())
                    .add(new FlameField(server, center, server.getGameTime() + FIELD_LIFETIME_TICKS));
            EXPLOSIONS.computeIfAbsent(owner.getUUID(), ignored -> new ArrayList<>())
                    .add(new FlameExplosion(server, center));
        }
        // 爆炸地形破坏：与领域方块破坏共用 /jjkoa terrain 开关（默认关闭）。
        if (JjkoaConfig.TERRAIN_DAMAGE) destroyExplosionTerrain(server, center);
        server.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4F, 0.75F);
        server.sendParticles(GeneratedMod.KITCHEN_FLAME.get(), center.x, center.y + 1.0D, center.z,
                220, 3.0D, 1.5D, 3.0D, 0.55D);
        // 起爆心火：落点处一团 2×2 大火焰，随后由编排接管。
        emitFlame2x2(server, center.x, center.y, center.z, 10);
        // 爆炸瞬间：直径 96 格内的玩家屏幕持续轻微颤动 5.5 秒。
        notifyBurst(server, center, SHAKE_TICKS, 0);
    }

    /**
     * 爆炸地形破坏：以爆点为球心删除半径 26 格球形的方块，并把爆点上方 80 格内、
     * 水平半径 26 格的柱体一并打通（火柱冲天）。与领域方块破坏同一套规则：
     * 基岩不删、未加载区块不动；由 /jjkoa terrain 开关控制（默认关闭）。
     */
    private static void destroyExplosionTerrain(ServerLevel server, Vec3 center) {
        final int radius = 26;
        final int above = 80;
        int centerX = Mth.floor(center.x);
        int centerY = Mth.floor(center.y);
        int centerZ = Mth.floor(center.z);
        int radiusSquared = radius * radius;
        int minY = Math.max(server.getMinBuildHeight(), centerY - radius);
        int maxY = Math.min(server.getMaxBuildHeight() - 1, centerY + above);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int horizontalSquared = dx * dx + dz * dz;
                if (horizontalSquared > radiusSquared) continue;
                for (int y = minY; y <= maxY; y++) {
                    int dy = y - centerY;
                    // 爆点以下只删球形部分；爆点以上整根半径 26 的圆柱删到 80 格。
                    if (dy < 0 && horizontalSquared + dy * dy > radiusSquared) continue;
                    BlockPos pos = new BlockPos(centerX + dx, y, centerZ + dz);
                    if (!server.hasChunkAt(pos)) continue;
                    var state = server.getBlockState(pos);
                    if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                        server.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /** 向爆炸中心直径 96 格内的玩家推送颤动/冲击帧。 */
    private static void notifyBurst(ServerLevel server, Vec3 center, int shakeTicks, int impactFrames) {
        if (shakeTicks <= 0 && impactFrames <= 0) return;
        double radiusSquared = FIELD_RADIUS * FIELD_RADIUS;
        for (ServerPlayer target : server.getEntitiesOfClass(ServerPlayer.class,
                new AABB(center.x - FIELD_RADIUS, center.y - FIELD_RADIUS, center.z - FIELD_RADIUS,
                        center.x + FIELD_RADIUS, center.y + FIELD_RADIUS, center.z + FIELD_RADIUS))) {
            double dx = target.getX() - center.x;
            double dy = target.getY() - center.y;
            double dz = target.getZ() - center.z;
            if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
            JjkoaNetwork.sendFlameBurst(target, shakeTicks, impactFrames);
        }
    }

    /** 在爆炸中心上方 2 格的 3×3×3 立方范围内随机取点放出一枚十字闪光（五发各自浮动）。 */
    private static void spawnFlash(ServerLevel server, Vec3 center,
                                   net.minecraft.core.particles.SimpleParticleType type) {
        RandomSource random = server.random;
        double x = center.x + (random.nextDouble() - 0.5D) * 3.0D;
        double y = center.y + 2.0D + (random.nextDouble() - 0.5D) * 3.0D;
        double z = center.z + (random.nextDouble() - 0.5D) * 3.0D;
        server.sendParticles(type, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /** 灶·开重置入口：在 (x, y, z) 处生成 count 朵占据 2×2 方块的大火焰，y 为火焰底部。 */
    public static void emitFlame2x2(ServerLevel server, double x, double y, double z, int count) {
        for (int i = 0; i < count; i++) {
            server.sendParticles(GeneratedMod.KITCHEN_FLAME_2X2.get(),
                    x, y, z, 1, 0.35D, 0.08D, 0.35D, 0.004D);
        }
    }

    private static void aimArrow(Player player, FlameArrowEntity arrow) {
        Vec3 direction = player.getLookAngle().normalize();
        // 视角水平右方向（与拉弓时弓箭所在的一侧一致）：look × up。
        Vec3 right = new Vec3(-direction.z, 0.0D, direction.x);
        if (right.lengthSqr() < 1.0E-8D) {
            // 垂直看向正上/正下方时水平分量退化，用朝向角兜底。
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            right = new Vec3(-Mth.cos(yaw), 0.0D, -Mth.sin(yaw));
        }
        Vec3 position = player.getEyePosition()
                .add(direction.scale(ARROW_DISTANCE))
                .add(right.x * ARROW_RIGHT_OFFSET, ARROW_HEIGHT_OFFSET, right.z * ARROW_RIGHT_OFFSET);
        arrow.aimAt(position, direction);
    }

    private static void removeArrow(ChargeState state) {
        if (state.arrow.isAlive()) state.arrow.discard();
    }

    private static void emitChargingParticles(ServerLevel server, Vec3 center) {
        server.sendParticles(GeneratedMod.KITCHEN_FLAME.get(), center.x, center.y, center.z,
                10, 0.22D, 0.22D, 0.22D, 0.08D);
    }

    private static void damageField(FlameField field, Player owner) {
        double radiusSquared = FIELD_RADIUS * FIELD_RADIUS;
        AABB area = new AABB(field.center.x - FIELD_RADIUS, field.center.y - 2.0D,
                field.center.z - FIELD_RADIUS, field.center.x + FIELD_RADIUS,
                field.center.y + DAMAGE_HEIGHT, field.center.z + FIELD_RADIUS);
        for (LivingEntity target : field.level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != owner && entity.isAlive())) {
            double dx = target.getX() - field.center.x;
            double dz = target.getZ() - field.center.z;
            if (dx * dx + dz * dz > radiusSquared) continue;
            double baseDamage = Math.max(2.0D, owner.getAttributeValue(Attributes.ATTACK_DAMAGE));
            float damage = (float) (baseDamage * ARROW_DAMAGE_MULTIPLIER * JjkoaConfig.DAMAGE_MULTIPLIER);
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            if (target.hurt(field.level.damageSources().playerAttack(owner), damage)) {
                target.invulnerableTime = 0;
                target.hurtTime = 0;
            }
            target.setSecondsOnFire(2);
        }
    }

    private static final class ChargeState {
        private final ServerLevel level;
        private final FlameArrowEntity arrow;
        private long lastInputTick;

        private ChargeState(ServerLevel level, FlameArrowEntity arrow, long lastInputTick) {
            this.level = level;
            this.arrow = arrow;
            this.lastInputTick = lastInputTick;
        }
    }

    /** 箭矢消失后、起爆前的引信状态。 */
    private static final class PendingImpact {
        private final ServerLevel level;
        private final Vec3 center;
        private final Player owner;
        private int ticksLeft = FUSE_TICKS;

        private PendingImpact(ServerLevel level, Vec3 center, Player owner) {
            this.level = level;
            this.center = center;
            this.owner = owner;
        }
    }

    private static final class FlameField {
        private final ServerLevel level;
        private final Vec3 center;
        private final long endTick;

        private FlameField(ServerLevel level, Vec3 center, long endTick) {
            this.level = level;
            this.center = center;
            this.endTick = endTick;
        }
    }
}
