package cn.blockforge.generated.sukunamod;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side implementation of skill 捌.
 * 命中不再产生击飞（还原 hurt 前的速度）；每次受伤在目标身上随机出现
 * 6 道 1 格长、0.2 秒播完的斩击帧动画，样式在三种斩击帧动画中随机。
 */
public final class EightSkill {
    private static final double HOLD_DAMAGE_MULTIPLIER = 1.2D;
    private static final double TELEPORT_DAMAGE_MULTIPLIER = 3.0D;
    private static final double TELEPORT_DISTANCE = 10.0D;
    private static final int TELEPORT_BOX_TICKS = 10; // 0.5 seconds
    private static final long MAX_HOLD_TICKS = 100L; // 捌长按最多 5 秒
    /** 捌的受击斩击：每次受伤在目标身上随机出 6 道、刀长 1 格、0.2 秒播完的帧动画斩击。 */
    private static final int BODY_SLASH_COUNT = 6;
    private static final int BODY_SLASH_LENGTH = 1;
    private static final double BODY_SLASH_LIFETIME_SECONDS = 0.2D;
    private static final Map<UUID, ActivePath> ACTIVE_PATHS = new HashMap<>();
    private static final Map<UUID, Long> LAST_HOLD_INPUT = new HashMap<>();
    private static final Map<UUID, Long> HOLD_STARTED = new HashMap<>();

    private EightSkill() { }

    /** Called once per client tick while X remains held. */
    public static void hold(Player player) {
        if (!(player.level() instanceof ServerLevel server)
                || !CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)) return;
        long now = server.getGameTime();
        UUID id = player.getUUID();
        long lastInput = LAST_HOLD_INPUT.getOrDefault(id, Long.MIN_VALUE);
        long started = HOLD_STARTED.getOrDefault(id, Long.MIN_VALUE);
        if (lastInput == Long.MIN_VALUE || now - lastInput > 2L) {
            if (!CursedEnergy.has(player, 20)
                    || !SkillCooldowns.tryUse(player, SkillCooldowns.EIGHT,
                    SkillCooldowns.EIGHT_COOLDOWN_TICKS)) return;
            started = now;
            HOLD_STARTED.put(id, started);
        }
        if (started != Long.MIN_VALUE && now - started >= MAX_HOLD_TICKS) {
            LAST_HOLD_INPUT.put(id, now);
            return;
        }
        if (!CursedEnergy.tryConsumeTimed(player, "eight_hold", 20, 2)) return;
        LAST_HOLD_INPUT.put(id, now);
        double centerX = player.getX();
        double centerY = player.getY() + player.getBbHeight() * 0.5D;
        double centerZ = player.getZ();
        AABB area = new AABB(centerX - 1.5D, centerY - 1.5D, centerZ - 1.5D,
                centerX + 1.5D, centerY + 1.5D, centerZ + 1.5D);
        damageEntities(server, player, area, HOLD_DAMAGE_MULTIPLIER, null);
    }

    /** Accelerates the server player one block per tick for ten ticks. */
    public static void teleport(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel server)
                || !CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)) return;
        long now = player.level().getGameTime();
        if (!CursedEnergy.has(player, 150)
                || !SkillCooldowns.tryUse(player, SkillCooldowns.EIGHT,
                SkillCooldowns.EIGHT_COOLDOWN_TICKS)) return;
        if (!CursedEnergy.tryConsume(player, 150)) return;
        Vec3 direction = player.getLookAngle().normalize();
        if (direction.lengthSqr() < 1.0E-8D) return;
        Vec3 start = player.position();
        Vec3 startCenter = start.add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
        Vec3 desiredCenter = startCenter.add(direction.scale(TELEPORT_DISTANCE));
        BlockHitResult blockHit = server.clip(new ClipContext(startCenter, desiredCenter,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double distance = TELEPORT_DISTANCE;
        if (blockHit.getType() != HitResult.Type.MISS) {
            distance = Math.max(0.0D, startCenter.distanceTo(blockHit.getLocation())
                    - player.getBbWidth() * 0.5D - 0.05D);
        }
        ActivePath path = new ActivePath(start, direction, distance, server, player);
        ACTIVE_PATHS.put(player.getUUID(), path);
        Vec3 destination = start.add(direction.scale(distance));
        player.connection.teleport(destination.x, destination.y, destination.z,
                player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        damagePath(path);
        path.remainingTicks = TELEPORT_BOX_TICKS;
    }

    /** Keeps the 10x1x2 path box alive for the requested half second. */
    public static void tick(Player player) {
        ActivePath path = ACTIVE_PATHS.get(player.getUUID());
        if (path == null || path.server != player.level() || !player.isAlive()) {
            ACTIVE_PATHS.remove(player.getUUID());
            long lastHold = LAST_HOLD_INPUT.getOrDefault(player.getUUID(), Long.MIN_VALUE);
            if (lastHold != Long.MIN_VALUE && player.level().getGameTime() - lastHold > 2L) {
                LAST_HOLD_INPUT.remove(player.getUUID());
                HOLD_STARTED.remove(player.getUUID());
            }
            return;
        }
        if (path.remainingTicks <= 0) {
            ACTIVE_PATHS.remove(player.getUUID());
            return;
        }
        damagePath(path);
        path.remainingTicks--;
    }

    private static void damagePath(ActivePath path) {
        Vec3 start = path.start;
        Vec3 direction = path.direction;
        Vec3 widthAxis = Math.abs(direction.y) > 0.9D
                ? direction.cross(new Vec3(1.0D, 0.0D, 0.0D)).normalize()
                : direction.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 heightAxis = widthAxis.cross(direction).normalize();
        Vec3 end = start.add(direction.scale(path.distance));
        AABB broadArea = new AABB(start, end).inflate(1.5D);
        for (LivingEntity target : path.server.getEntitiesOfClass(LivingEntity.class, broadArea,
                entity -> entity != path.owner && entity.isAlive() && !path.hitEntities.contains(entity.getUUID()))) {
            Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            Vec3 relative = center.subtract(start);
            double along = relative.dot(direction);
            double width = Math.abs(relative.dot(widthAxis));
            double height = Math.abs(relative.dot(heightAxis));
            double radius = target.getBbWidth() * 0.5D;
            if (along < -radius || along > path.distance + radius
                    || width > 0.5D + radius || height > 1.0D + target.getBbHeight() * 0.5D) continue;
            damageTarget(path.server, path.owner, target, TELEPORT_DAMAGE_MULTIPLIER);
            path.hitEntities.add(target.getUUID());
        }
    }

    private static void damageEntities(ServerLevel server, Player owner, AABB area, double multiplier,
                                       Set<UUID> alreadyHit) {
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != owner && entity.isAlive()
                        && (alreadyHit == null || !alreadyHit.contains(entity.getUUID())))) {
            damageTarget(server, owner, target, multiplier);
            if (alreadyHit != null) alreadyHit.add(target.getUUID());
        }
    }

    private static void damageTarget(ServerLevel server, Player owner, LivingEntity target, double multiplier) {
        ItemStack held = owner.getMainHandItem();
        double heldDamage = Math.max(2.0D, owner.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float damage = (float) (heldDamage * multiplier * JjkoaConfig.DAMAGE_MULTIPLIER);
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        // 捌不再造成击飞：原版 hurt() 对一切带攻击者的伤害源统一施加 0.4 档通用击退
        // （LivingEntity#hurt 里的 knockback(0.4, dx, dz)）。这里先快照受击瞬间的速度，
        // 结算完原样还原——伤害、着火、受击动画照常，目标不再被削飞。
        Vec3 motionBefore = target.getDeltaMovement();
        boolean impulseBefore = target.hasImpulse;
        // 捌无视护甲与护甲韧性：伤害类型换成 minecraft:generic——它属于原版
        // bypasses_armor 标签，LivingEntity#getDamageAfterArmorAbsorb 命中该标签时
        // 直接跳过 CombatRules.getDamageAfterAbsorb(伤害, 护甲值, 韧性) 整条公式，
        // 护甲与韧性都不再减伤；保护类附魔仍正常生效（只按要求绕过护甲）。
        boolean damaged = target.hurt(armorPiercingSource(server, owner), damage);
        target.setDeltaMovement(motionBefore);
        target.hasImpulse = impulseBefore;
        target.hurtMarked = true; // Forge 1.20.1：置位后 ServerEntity 会把还原后的动量下发给客户端
        if (damaged) {
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            int fire = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, held);
            if (fire > 0) target.setSecondsOnFire(fire * 4);
            // 每受到一次伤害出现一次：身上随机位置炸出 6 道帧动画斩击。
            spawnBodySlashes(server, owner, target);
        }
    }

    /**
     * 捌专用伤害源：minecraft:generic 伤害类型（原版 bypasses_armor 成员，护甲与韧性
     * 全不参与减伤），但归属人仍是释放技能的玩家——击杀统计、怪物仇恨都记在玩家头上。
     * getMsgId 覆写成模组自己的死亡消息键，避免 generic 默认“莫名其妙就死了”的文案。
     */
    private static DamageSource armorPiercingSource(ServerLevel server, Player owner) {
        Holder<DamageType> type = server.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.GENERIC);
        return new DamageSource(type, owner, owner) {
            @Override
            public String getMsgId() {
                return "sukunamod.eight";
            }
        };
    }

    /**
     * 捌的受击斩击特效：在目标包围盒内随机取 6 个点，各放一道刀长 1 格、寿命 0.2 秒
     * （4 tick）的斩击帧动画。每道独立地在三种斩击帧动画——原斩击（slash_frame
     * 1→2→3→4）/红边斩击/红色斩击——里随机挑一种，面片法线朝向施放者眼睛
     * （与乱解同一套朝向约定），任何角度看都是完整刀身。
     */
    private static void spawnBodySlashes(ServerLevel server, Player owner, LivingEntity target) {
        AABB box = target.getBoundingBox();
        Vec3 viewerEye = owner.getEyePosition();
        RandomSource random = server.random;
        for (int i = 0; i < BODY_SLASH_COUNT; i++) {
            Vec3 pos = new Vec3(
                    Mth.lerp(random.nextDouble(), box.minX, box.maxX),
                    Mth.lerp(random.nextDouble(), box.minY, box.maxY),
                    Mth.lerp(random.nextDouble(), box.minZ, box.maxZ));
            DismantleProjectile slash = new DismantleProjectile(
                    GeneratedMod.DISMANTLE_PROJECTILE.get(), server);
            slash.setOwner(owner);
            slash.setPos(pos.x, pos.y, pos.z);
            slash.setVisualOnly(BODY_SLASH_LIFETIME_SECONDS, BODY_SLASH_LENGTH);
            slash.setSlashStyle(random.nextInt(3));
            slash.setSlashAngle(random.nextFloat() * (float) (Math.PI * 2.0D));
            faceViewer(slash, pos, viewerEye);
            server.addFreshEntity(slash);
        }
    }

    /** 让斩击面片的法线指向观察者眼睛（与乱解同一套朝向约定）。 */
    private static void faceViewer(DismantleProjectile slash, Vec3 position, Vec3 viewerEye) {
        Vec3 facing = viewerEye.subtract(position);
        if (facing.lengthSqr() < 1.0E-6D) return;
        Vec3 normalized = facing.normalize();
        slash.setYRot((float) (Math.atan2(normalized.z, normalized.x) * 180.0D / Math.PI) - 90.0F);
        slash.setXRot((float) (-Math.atan2(normalized.y,
                Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z)) * 180.0D / Math.PI));
    }

    private static final class ActivePath {
        private final Vec3 start;
        private final Vec3 direction;
        private final double distance;
        private final ServerLevel server;
        private final ServerPlayer owner;
        private final Set<UUID> hitEntities = new HashSet<>();
        private int remainingTicks;

        private ActivePath(Vec3 start, Vec3 direction, double distance, ServerLevel server, ServerPlayer owner) {
            this.start = start;
            this.direction = direction;
            this.distance = distance;
            this.server = server;
            this.owner = owner;
        }
    }
}
