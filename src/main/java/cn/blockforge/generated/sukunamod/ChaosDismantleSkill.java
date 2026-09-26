package cn.blockforge.generated.sukunamod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 技能6"乱解"：以施放者为中心的 9x9x9 立方内斩击 7 次，每 0.15 秒（3 tick）
 * 一刀，整轮约 0.9 秒（含末刀动画约 1.1 秒）。每一刀：
 * 1. 播放一次「解」的宿傩斩击音效；
 * 2. 在 9x9x9 范围内随机位置放出 45 道斩击帧动画（每道从三种样式——原斩击/红边
 *    斩击/红色斩击——里随机挑一种，刀长 5~10 格），0.2 秒（4 tick）内播完；
 * 3. 对范围内除施放者外的所有生物结算一次伤害（倍率 2.4，随 /jjkoa damage 缩放）；
 * 4. 方块破坏与领域共用 /jjkoa terrain 开关：开启时删除范围内的所有方块，
 *    但永远保留施放者脚底下（及其所处一格），施放者不会因此坠落；
 * 5. 给施放者推一次 185% 振幅的方向性震屏，各刀方向不同；同时挂上
 *    "柔和暗化 + 中度色差"的乱解后处理链（见 DomainVisuals / sukuna_chaos）。
 *    后处理链的续期只跟到最后一刀的斩击帧动画播完为止——最后一刀落下的 0.2 秒
 *    后色差与暗化立即撤掉，不会在斩击消失后再拖一段尾巴。
 * 一轮乱解（整 7 刀）在施放开始时一次性消耗 380 咒力；咒力不足则整轮不放，
 * 也不会进入冷却。
 */
public final class ChaosDismantleSkill {
    /** 判定盒半边长：9x9x9 以施放者身体中心为中心。 */
    private static final double AREA_HALF = 4.5D;
    /** 斩击次数：7 次。45 是每刀铺出的斩击动画帧道数，不是刀数。 */
    private static final int SLASH_COUNT = 7;
    /** 斩击节奏：每 0.15 秒（3 tick）一刀。 */
    private static final int SLASH_INTERVAL_TICKS = 3;
    /** 每刀的斩击帧动画道数与寿命：45 道、0.2 秒。 */
    private static final int FRAMES_PER_SLASH = 45;
    private static final double FRAME_LIFETIME_SECONDS = 0.2D;
    /** 刀长 5~10 格随机：贴合 9 格见方的近距范围，也不会糊成一片。 */
    private static final int BLADE_MIN_LENGTH = 5;
    private static final int BLADE_MAX_LENGTH = 10;
    /** 伤害倍率 2.4（高于网格斩档），仍乘全局 /jjkoa damage 系数。 */
    private static final double DAMAGE_MULTIPLIER = 2.4D;
    /** 每刀一次 185% 振幅的方向性震屏，持续 0.25 秒。 */
    private static final int SHAKE_TICKS = 5;
    private static final int SHAKE_PERCENT = 185;
    /**
     * 后处理链续期时长：0.2 秒，恰好等于一记斩击帧动画的寿命。每 3 tick 一刀持续
     * 续期，链在整轮中不会断；最后一刀落下 0.2 秒后——也就是最后一道斩击消失的
     * 同一刻——色差与暗化立即撤掉，不留残影尾巴。
     */
    private static final int VISUAL_TICKS = 4;
    /** 一次乱解（整轮 7 刀）的咒力消耗：380。 */
    private static final int ENERGY_COST = 380;

    /** 每个玩家至多一场进行中的乱解：7 刀排期，放完即清。 */
    private static final class Casting {
        private int fired;
        private long nextFireAt;
        /** 施放时的实体 id：死亡重生会换实例，旧排期不能续到新的身上。 */
        private final int ownerEntityId;
        /** 施放时所在维度：换维度后端点不再续打。 */
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;

        private Casting(int ownerEntityId,
                        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
            this.ownerEntityId = ownerEntityId;
            this.dimension = dimension;
        }
    }

    private static final Map<UUID, Casting> ACTIVE = new HashMap<>();

    private ChaosDismantleSkill() { }

    /** 数字 6 按下：立刻开始第一轮斩击（第 1 刀就在本 tick 落）。 */
    public static void cast(Player player) {
        if (!(player.level() instanceof ServerLevel server)
                || !CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)
                || !player.isAlive()) return;
        if (ACTIVE.containsKey(player.getUUID())) return; // 一轮乱解没放完不允许叠加
        // 一次乱解整轮消耗 380 咒力：先查够不够，再进冷却，最后扣——
        // 咒力不足时整轮不放，也不会白白进入冷却（与灶·开、八方同一写法）。
        if (!CursedEnergy.has(player, ENERGY_COST)
                || !SkillCooldowns.tryUse(player, SkillCooldowns.LUANJIE,
                SkillCooldowns.LUANJIE_COOLDOWN_TICKS)) return;
        if (!CursedEnergy.tryConsume(player, ENERGY_COST)) return;
        Casting casting = new Casting(player.getId(), player.level().dimension());
        casting.nextFireAt = server.getGameTime();
        ACTIVE.put(player.getUUID(), casting);
    }

    /** 挂在施放者的服务端 tick 上：到点补发落下的斩击，7 刀放完自动收尾。 */
    public static void tick(Player player) {
        Casting casting = ACTIVE.get(player.getUUID());
        if (casting == null) return;
        if (!(player.level() instanceof ServerLevel server) || !player.isAlive()
                || !CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)
                || casting.ownerEntityId != player.getId()
                || !casting.dimension.equals(server.dimension())) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        if (server.getGameTime() < casting.nextFireAt) return;
        // 每 tick 至多补一刀：卡服时让剩余斩击顺延，而不是同一 tick 全部砸下。
        fireSlash(server, player, casting.fired);
        casting.fired++;
        casting.nextFireAt += SLASH_INTERVAL_TICKS;
        if (casting.fired >= SLASH_COUNT) ACTIVE.remove(player.getUUID());
    }

    /** 下线清理：进行中的乱解随人取消，避免 UUID 槽位挡住下次进服。 */
    public static void onOwnerDisconnect(Player player) {
        ACTIVE.remove(player.getUUID());
    }

    /** 单刀乱解：音效 + 45 道随机斩击帧动画 + 范围伤害 + 范围破坏 + 定向震屏。 */
    private static void fireSlash(ServerLevel server, Player player, int index) {
        Vec3 center = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
        AABB area = new AABB(center.x - AREA_HALF, center.y - AREA_HALF, center.z - AREA_HALF,
                center.x + AREA_HALF, center.y + AREA_HALF, center.z + AREA_HALF);
        // 「解」的宿傩斩击音效：每刀一响，音色与普通解施放一致。
        server.playSound(null, center.x, center.y, center.z,
                GeneratedMod.SUKUNA_SLASH_SOUND.get(), SoundSource.PLAYERS, 1.1F,
                0.97F + server.random.nextFloat() * 0.07F);
        // 45 道斩击帧动画：均匀撒在 9x9x9 内，每道独立随机样式（0/1/2 三选一）、
        // 随机刀长与平面内转角，面片法线朝向施放者眼睛，任何角度看都是整幅刀身。
        Vec3 viewerEye = player.getEyePosition();
        for (int i = 0; i < FRAMES_PER_SLASH; i++) {
            Vec3 pos = new Vec3(Mth.lerp(server.random.nextDouble(), area.minX, area.maxX),
                    Mth.lerp(server.random.nextDouble(), area.minY, area.maxY),
                    Mth.lerp(server.random.nextDouble(), area.minZ, area.maxZ));
            int length = BLADE_MIN_LENGTH + server.random.nextInt(
                    BLADE_MAX_LENGTH - BLADE_MIN_LENGTH + 1);
            DismantleProjectile slash = new DismantleProjectile(
                    GeneratedMod.DISMANTLE_PROJECTILE.get(), server);
            slash.setOwner(player);
            slash.setPos(pos.x, pos.y, pos.z);
            slash.setVisualOnly(FRAME_LIFETIME_SECONDS, length);
            slash.setSlashStyle(server.random.nextInt(3));
            slash.setSlashAngle(server.random.nextFloat() * (float) (Math.PI * 2.0D));
            faceViewer(slash, pos, viewerEye);
            server.addFreshEntity(slash);
        }
        // 伤害：范围内除施放者外的所有生物，每刀各结算一次。
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && entity.isAlive())) {
            damageTarget(server, player, target);
        }
        // 方块破坏：与领域/世界斩共用 /jjkoa terrain 开关，脚底下的方块永远保留。
        if (JjkoaConfig.TERRAIN_DAMAGE) destroyAreaExceptUnderFeet(server, player, area);
        // 方向性震屏：第 index 刀取本扇区内的随机角，每四刀轮转一圈象限，连续几刀方向必不相同。
        if (player instanceof ServerPlayer serverPlayer) {
            JjkoaNetwork.sendChaosStrike(serverPlayer, SHAKE_TICKS, SHAKE_PERCENT,
                    slashShakeDegrees(index, server), VISUAL_TICKS);
        }
    }

    /** 第 n 刀的震屏方向角：四等分象限轮转 + 象限内 ±20° 抖动。 */
    private static float slashShakeDegrees(int index, ServerLevel server) {
        return index * 90.0F + 45.0F + server.random.nextFloat() * 40.0F - 20.0F;
    }

    /** 让视觉斩击面片的法线指向观察者（与领域斩击风暴同一套朝向约定）。 */
    private static void faceViewer(DismantleProjectile slash, Vec3 position, Vec3 viewerEye) {
        Vec3 facing = viewerEye.subtract(position);
        if (facing.lengthSqr() < 1.0E-6D) return;
        Vec3 normalized = facing.normalize();
        slash.setYRot((float) (Math.atan2(normalized.z, normalized.x) * 180.0D / Math.PI) - 90.0F);
        slash.setXRot((float) (-Math.atan2(normalized.y,
                Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z)) * 180.0D / Math.PI));
    }

    private static void damageTarget(ServerLevel server, Player player, LivingEntity target) {
        ItemStack held = player.getMainHandItem();
        double heldDamage = Math.max(2.0D, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float damage = (float) (heldDamage * DAMAGE_MULTIPLIER * JjkoaConfig.DAMAGE_MULTIPLIER);
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        boolean damaged = target.hurt(server.damageSources().playerAttack(player), damage);
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        if (damaged) {
            int fire = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, held);
            if (fire > 0) target.setSecondsOnFire(fire * 4);
        }
    }

    /**
     * 删除 9x9x9 范围内的所有方块（基岩除外、直接换空气不掉落实体），
     * 但跳过施放者当前所处格与其正下方一格——站在地上放乱解不会把自己摔下去。
     */
    private static void destroyAreaExceptUnderFeet(ServerLevel server, Player player, AABB area) {
        BlockPos feet = player.blockPosition();
        BlockPos underFeet = feet.below();
        int minX = Mth.floor(area.minX);
        int maxX = Mth.ceil(area.maxX) - 1;
        int minY = Mth.floor(area.minY);
        int maxY = Mth.ceil(area.maxY) - 1;
        int minZ = Mth.floor(area.minZ);
        int maxZ = Mth.ceil(area.maxZ) - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pos.equals(feet) || pos.equals(underFeet)) continue;
                    var state = server.getBlockState(pos);
                    if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                        server.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }
}
