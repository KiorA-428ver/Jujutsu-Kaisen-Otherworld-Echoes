package cn.blockforge.generated.sukunamod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-side implementation of the open domain expansion. */
public final class DomainSkill {
    private static final int RADIUS_BLOCKS = 128;
    private static final double RADIUS = RADIUS_BLOCKS;
    private static final int CHARGE_TICKS = 30; // 按下后延迟 1.5 秒生成领域
    private static final int PRECAST_RADIUS_BLOCKS = 32;
    private static final double PRECAST_RADIUS = PRECAST_RADIUS_BLOCKS;
    private static final double DAMAGE_MULTIPLIER = 1.3D;
    /**
     * 边界环点数随半径加密：约每 3 格一个粒子，128 格半径一圈 ~269 点，
     * 避免半径放大后环变成稀疏的"珠子"而看不出圆。
     */
    private static final int RING_POINTS = (int) Math.ceil(2.0D * Math.PI * RADIUS / 3.0D);
    /**
     * 方块破坏的节奏：不再写死"每 tick 多少格"，而是按当前半径的实际体积反推预算，
     * 让扫描波总是约 15 秒（300 tick）从圆心推到边界。半径改动后无需重新调参，
     * 也顺带把每 tick 的工作量降到原来的三分之一左右（120k/tick 在 128 半径下
     * 单 tick 就要 100ms 以上，会把 TPS 直接砸穿）。
     */
    private static final int TERRAIN_SCAN_TARGET_TICKS = 300;
    private static final int TERRAIN_BUDGET_MIN = 4_000;
    private static final int TERRAIN_BUDGET_MAX = 120_000;
    /** 每 2 tick（0.1 秒）放出新一波斩击。 */
    private static final int SLASH_STORM_INTERVAL_TICKS = 2;
    /** 每波 130 道斩击（按要求在 100 道基础上加密 30 道）。 */
    private static final int SLASHES_PER_STORM = 130;
    /** 单道斩击 0.15 秒（3 tick）内把所选样式的帧动画完整播完。 */
    private static final double SLASH_STORM_LIFETIME_SECONDS = 0.15D;
    /**
     * 斩击落点：每道 65% 概率出现在神龛水平半径 32 格内，其余 35% 出现在
     * 领域范围（半径 128 格圆）内、神龛 32 格圆之外的任意一处。
     * 高度按落点与神龛的水平距离分两套规则：
     * 32 格内 35% 直接在神龛当前水平位置、65% 在神龛上方 6~12 格内浮动；
     * 32 格外一律带高度差：75% 概率在上方 0~60 格内浮动，
     * 25% 概率在下方 0~40 格内浮动。
     */
    private static final double SHRINE_INNER_RADIUS = 32.0D;
    private static final double SHRINE_NEAR_CHANCE = 0.65D;
    /** 神龛半径 32 格内，斩击直接在当前水平位置（无高度差）的概率。 */
    private static final double SHRINE_FLAT_CHANCE = 0.35D;
    /** 神龛半径 32 格内（65%），斩击在神龛水平位置上方 6~12 格内浮动。 */
    private static final double SHRINE_FLOAT_MIN_ABOVE = 6.0D;
    private static final double SHRINE_FLOAT_MAX_ABOVE = 12.0D;
    /** 有高度差时：75% 概率在神龛水平位置上方，浮动区间 0~60 格。 */
    private static final double STORM_ABOVE_CHANCE = 0.75D;
    private static final double STORM_ABOVE_RANGE = 60.0D;
    /** 斩击高度：25% 概率在神龛水平位置下方，浮动区间 0~40 格。 */
    private static final double STORM_BELOW_RANGE = 40.0D;
    /**
     * 斩击长度区间：三种样式（原斩击/红边斩击/红色斩击）的每一刀长度都在
     * 15~65 格之间随机浮动（含两端），每发独立取随机。
     */
    private static final int SLASH_STORM_MIN_LENGTH = 15;
    private static final int SLASH_STORM_MAX_LENGTH = 65;
    private static final Map<UUID, ActiveDomain> ACTIVE_DOMAINS = new HashMap<>();
    private static final Map<UUID, ChargingDomain> CHARGING_DOMAINS = new HashMap<>();
    /**
     * 本会话内所有由领域生成的神龛，关闭领域时按这张表清理。
     * 刻意不做"全维度 AABB 实体查询"：1.20.1 的实体分区检索会按 AABB 换算出的
     * 区块分区区间三重循环遍历（±3e7 格 ≈ 每轴 190 万个分区），一次就能把服务端
     * 主线程彻底卡死，表里过一遍是同一件事的正确做法。
     */
    private static final Set<ShrineEntity> LIVE_SHRINES = new HashSet<>();
    private static final DustParticleOptions DOMAIN_RING = new DustParticleOptions(
            new Vector3f(0.12F, 0.015F, 0.02F), 0.2F);

    private DomainSkill() { }

    /** Starts the 1.5-second domain delay on key press; an active domain closes on the next press. */
    public static void hold(Player player) {
        if (!(player.level() instanceof ServerLevel server)
                || !CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)
                || !player.isAlive()) return;

        if (ACTIVE_DOMAINS.containsKey(player.getUUID())) {
            close(player);
            return;
        }
        if (!SkillCooldowns.isReady(player, SkillCooldowns.DOMAIN)
                || !CursedEnergy.has(player, 10)
                || CHARGING_DOMAINS.containsKey(player.getUUID())) return;

        long now = server.getGameTime();
        ChargingDomain charging = new ChargingDomain(now);
        CHARGING_DOMAINS.put(player.getUUID(), charging);
        applyPrecastSlow(server, player, charging);
        // 前摇开始：周围玩家的环境在 1.5 秒内缓慢染成暗红（雾与天空同源自雾色）。
        broadcastVisual(server, player.position(), RADIUS, JjkoaNetwork.DOMAIN_VISUAL_PRECAST);
        // 前摇（刚开始读条）时播放一次"伏魔御厨子前摇"。
        charging.musicNotified.addAll(playDomainSound(server, player.position(),
                GeneratedMod.DOMAIN_CHARGE_SOUND.get(), 1.2F));
    }

    /** Kept for packet compatibility; releasing no longer cancels the press-triggered delay. */
    public static void release(Player player) {
        // The domain is intentionally generated 1.5 seconds after the press, even if the key is released.
    }

    /**
     * 神龛生成点：界主身后 4 格——沿视线水平方向的反向量偏移；垂直俯视/仰头到
     * 水平分量几乎为零时退回用 yaw 算水平向量，保证神龛永远落在正后方。
     * 神龛模型的嘴部在模型 -Z 面（即实体朝向面），实体 yaw 取界主 yaw 时
     * 嘴部恰好正对界主背部。
     */
    private static Vec3 shrineAnchor(Player player, Vec3 center) {
        Vec3 look = player.getLookAngle();
        double hx = look.x;
        double hz = look.z;
        double horizontal = Math.sqrt(hx * hx + hz * hz);
        if (horizontal < 1.0E-4D) {
            double radians = Math.toRadians(player.getYRot());
            hx = -Math.sin(radians);
            hz = Math.cos(radians);
            horizontal = 1.0D;
        }
        return new Vec3(center.x - hx / horizontal * 4.0D, center.y, center.z - hz / horizontal * 4.0D);
    }

    /** A release packet is harmless during the delayed cast; an active domain closes on the next press. */
    public static void releaseOrClose(Player player) {
        if (ACTIVE_DOMAINS.containsKey(player.getUUID())) close(player);
    }

    /** Compatibility entry point for older callers: an active domain closes, otherwise charging starts. */
    public static void toggle(Player player) {
        if (ACTIVE_DOMAINS.containsKey(player.getUUID())) close(player);
        else hold(player);
    }

    /** 展开领域：神龛在界主身后 4 格生成，嘴部正对界主背部。 */
    public static void cast(Player player) {
        if (!(player.level() instanceof ServerLevel server)
                || !CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)) return;
        Vec3 center = player.position();
        Vec3 shrineAnchor = shrineAnchor(player, center);
        ActiveDomain old = ACTIVE_DOMAINS.remove(player.getUUID());
        if (old != null) {
            clearAllShrines(old.level, old);
            // 旧领域被新领域顶掉：先把上一首"伏魔御厨子"停掉再放新的，避免两首叠着响。
            stopDomainMusic(old.level, old.musicNotified, GeneratedMod.DOMAIN_EXPAND_SOUND.get());
            broadcastVisualOff(old.level, old, player);
        } else {
            // 首次展开也顺手清掉本维度里历史遗留（崩溃/重启后保存下来的）神龛。
            clearAllShrines(server, null);
        }
        ActiveDomain domain = new ActiveDomain(server, center, player.getId());
        domain.spawnShrine(shrineAnchor, player.getYRot());
        ACTIVE_DOMAINS.put(player.getUUID(), domain);
        server.sendParticles(ParticleTypes.ENCHANT, center.x, center.y + 1.0D, center.z,
                32, 1.0D, 1.0D, 1.0D, 0.2D);
        // 神龛展开（领域开启）时播放一次"伏魔御厨子"。
        domain.musicNotified.addAll(playDomainSound(server, center,
                GeneratedMod.DOMAIN_EXPAND_SOUND.get(), 1.4F));
        sendBoundary(server, center, player instanceof ServerPlayer owner ? owner : null);
        syncDomainState(player, true);
        // 领域开启：范围内的玩家立即满额暗红 + 12 秒轻微震颤 + 迷雾/暗化/色差，
        // 并登记进通知名单，关闭时按名单回收。
        broadcastVisual(server, center, RADIUS, JjkoaNetwork.DOMAIN_VISUAL_ACTIVE, domain);
    }

    /** Maintains each player's domain, charge, slowdown and sure-hit damage. */
    public static void tick(Player player) {
        UUID id = player.getUUID();
        ChargingDomain charging = CHARGING_DOMAINS.get(id);
        if (charging != null) {
            if (!(player.level() instanceof ServerLevel server)
                    || !player.isAlive() || !CombatMode.isServerActive(player)
                    || !CombatMode.isUnlocked(player)) {
                cancelCharging(player);
            } else if (server.getGameTime() - charging.startedAt >= CHARGE_TICKS) {
                CHARGING_DOMAINS.remove(id);
                clearPrecastSlow(server, player, charging);
                // 读条走完：前摇音乐还剩的尾巴也收掉，别和展开音乐叠在一起。
                stopDomainMusic(server, charging.musicNotified, GeneratedMod.DOMAIN_CHARGE_SOUND.get());
                if (SkillCooldowns.isReady(player, SkillCooldowns.DOMAIN)
                        && CursedEnergy.has(player, 10)) {
                    cast(player);
                } else {
                    // 前摇走完却没能展开（咒力/冷却不满足）：立即通知界主回落；
                    // 其他收到过前摇渐红的玩家由客户端 40 tick 超时自愈。
                    broadcastVisualOff(server, null, player);
                }
            } else {
                applyPrecastSlow(server, player, charging);
            }
        }

        ActiveDomain domain = ACTIVE_DOMAINS.get(id);
        if (domain == null) return;
        if (domain.level != player.level() || !player.isAlive()
                || !CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)
                || domain.ownerEntityId != player.getId()
                || !(player.level() instanceof ServerLevel server)) {
            clearAllShrines(domain.level, domain);
            // 死亡/换维度/退出战斗模式等被动结束：同样把"伏魔御厨子"停掉。
            stopDomainMusic(domain.level, domain.musicNotified, GeneratedMod.DOMAIN_EXPAND_SOUND.get());
            ACTIVE_DOMAINS.remove(id);
            syncDomainState(player, false);
            // 界主死亡/失效时他自己往往正是"收不到圈内广播"的那个人（死亡被
            // isAlive 过滤、换维度已不在本维度），必须按通知名单回收并单独发给他。
            broadcastVisualOff(domain.level, domain, player);
            return;
        }
        // 设定：界主走出领域范围（水平半径外）后立刻结束领域。
        if (JjkoaConfig.LEAVE_CLOSES_DOMAIN) {
            double awayX = player.getX() - domain.center.x;
            double awayZ = player.getZ() - domain.center.z;
            if (awayX * awayX + awayZ * awayZ > RADIUS * RADIUS) {
                close(player);
                return;
            }
        }
        if (!CursedEnergy.tryConsumeTimed(player, "domain", 10, 2)) {
            close(player);
            return;
        }
        damageEntities(server, player, domain.center);
        destroyDomainTerrain(server, domain);
        // 每 0.1 秒（2 tick）放出一波 130 道斩击，每道 0.15 秒内播完动画。
        if (server.getGameTime() % SLASH_STORM_INTERVAL_TICKS == 0L) spawnSlashStorm(server, domain);
        // The sweep sound is intentionally less frequent so the cut storm does not
        // turn into an uninterrupted wall of audio.
        if (server.getGameTime() % 4L == 0L) playDomainSweepSound(server, domain.center);
        if (server.getGameTime() % 10L == 0L) {
            sendBoundary(server, domain.center, player instanceof ServerPlayer owner ? owner : null);
            syncDomainAura(server, domain);
        }
    }

    private static void cancelCharging(Player player) {
        ChargingDomain charging = CHARGING_DOMAINS.remove(player.getUUID());
        if (charging != null && player.level() instanceof ServerLevel server) {
            clearPrecastSlow(server, player, charging);
            stopDomainMusic(server, charging.musicNotified, GeneratedMod.DOMAIN_CHARGE_SOUND.get());
            // 前摇中止：单独通知界主回落；其他收到过"渐红"的玩家由客户端
            // 40 tick 前摇超时自愈。
            broadcastVisualOff(server, null, player);
        }
    }

    private static void close(Player player) {
        cancelCharging(player);
        ActiveDomain existing = ACTIVE_DOMAINS.remove(player.getUUID());
        if (existing == null) return;
        clearAllShrines(existing.level, existing);
        // 关闭领域：把还在响的"伏魔御厨子"同步停掉（44 秒的音乐不会跟着领域一起结束）。
        stopDomainMusic(existing.level, existing.musicNotified, GeneratedMod.DOMAIN_EXPAND_SOUND.get());
        SkillCooldowns.applyDomainClose(player);
        syncDomainState(player, false);
        broadcastVisualOff(existing.level, existing, player);
    }

    /**
     * 界主下线时的收尾：领域的全部维护都挂在界主自己的 tick 上，人一走它就变成
     * "没人 tick 却仍在生效"的僵尸状态——神龛留在原地，圈内玩家再也收不到 OFF 包，
     * 会一直卡在暗红雾、屏幕暗化和色差后处理里。这里按 close 的流程完整关掉。
     */
    public static void onOwnerDisconnect(Player player) {
        ActiveDomain domain = ACTIVE_DOMAINS.remove(player.getUUID());
        ChargingDomain charging = CHARGING_DOMAINS.remove(player.getUUID());
        if (charging != null && player.level() instanceof ServerLevel server) {
            clearPrecastSlow(server, player, charging);
            stopDomainMusic(server, charging.musicNotified, GeneratedMod.DOMAIN_CHARGE_SOUND.get());
        }
        if (domain == null) return;
        clearAllShrines(domain.level, domain);
        stopDomainMusic(domain.level, domain.musicNotified, GeneratedMod.DOMAIN_EXPAND_SOUND.get());
        syncDomainState(player, false);
        broadcastVisualOff(domain.level, domain, player);
    }

    /**
     * 关闭领域时清除该维度的所有神龛：既收掉本次领域绑定的神龛，也把本会话里
     * 已经没有任何领域引用的"孤儿"神龛一并删除；其他仍然开着的领域的神龛不受影响。
     * 跨重启遗留的孤儿由"神龛不入存档 + 实体加载守卫"两道保险处理（见
     * ShrineEntity#shouldBeSaved 与 JjkoaPlayerEvents#onShrineLoadedFromDisk）。
     */
    private static void clearAllShrines(ServerLevel server, ActiveDomain closing) {
        if (closing != null) closing.removeShrine();
        Iterator<ShrineEntity> it = LIVE_SHRINES.iterator();
        while (it.hasNext()) {
            ShrineEntity shrine = it.next();
            if (shrine.isRemoved()) {
                it.remove();
                continue;
            }
            if (shrine.level() != server || ownsShrine(shrine)) continue;
            shrine.discard();
            it.remove();
        }
    }

    /** 这个神龛是否仍被某个活动领域持有（多领域共存时互不误伤）。 */
    private static boolean ownsShrine(ShrineEntity shrine) {
        for (ActiveDomain domain : ACTIVE_DOMAINS.values()) {
            if (domain.shrine == shrine) return true;
        }
        return false;
    }

    private static void syncDomainState(Player player, boolean active) {
        if (player instanceof ServerPlayer serverPlayer) {
            JjkoaNetwork.syncDomainState(serverPlayer, active);
        }
    }

    /**
     * 以 center 为圆心、radius 为水平半径，把领域视觉模式广播给圈内玩家。
     * mode 取 JjkoaNetwork.DOMAIN_VISUAL_PRECAST/ACTIVE/OFF。
     */
    private static void broadcastVisual(ServerLevel server, Vec3 center, double radius, int mode) {
        broadcastVisual(server, center, radius, mode, null);
    }

    /**
     * 同上；当 mode 为 ACTIVE 且传入 domain 时，把收到包的玩家登记进该领域的
     * 通知名单（visualNotified）。关闭领域时按这份名单精确回收——"圈内广播 OFF"
     * 永远救不回已经走出圈外或已经死亡的界主本人，这正是色差/暗红滤镜残留的根因。
     */
    private static void broadcastVisual(ServerLevel server, Vec3 center, double radius, int mode,
                                        ActiveDomain domain) {
        double radiusSquared = radius * radius;
        AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        for (ServerPlayer target : server.getEntitiesOfClass(ServerPlayer.class, box,
                Player::isAlive)) {
            double dx = target.getX() - center.x;
            double dz = target.getZ() - center.z;
            if (dx * dx + dz * dz <= radiusSquared) {
                JjkoaNetwork.sendDomainVisual(target, mode);
                if (domain != null && mode == JjkoaNetwork.DOMAIN_VISUAL_ACTIVE) {
                    domain.visualNotified.add(target.getUUID());
                }
            }
        }
    }

    /**
     * 回收领域视觉：给这份名单里所有收到过 ACTIVE 的玩家发 OFF（跨维度也发，
     * 玩家实例按 UUID 从服务器全局表查），再单独发给界主本人——他可能已经死亡、
     * 走出圈外或换了维度，是"按圈回收"最容易漏掉的人。名单随后清空。
     */
    private static void broadcastVisualOff(ServerLevel server, ActiveDomain domain, Player owner) {
        if (domain != null) {
            for (UUID target : domain.visualNotified) {
                ServerPlayer player = server.getServer().getPlayerList().getPlayer(target);
                if (player != null) JjkoaNetwork.sendDomainVisual(player, JjkoaNetwork.DOMAIN_VISUAL_OFF);
            }
            domain.visualNotified.clear();
        }
        if (owner instanceof ServerPlayer ownerPlayer) {
            JjkoaNetwork.sendDomainVisual(ownerPlayer, JjkoaNetwork.DOMAIN_VISUAL_OFF);
        }
    }

    /**
     * 领域存续期间每 0.5 秒重新对账一次：圈内玩家收 ACTIVE（幂等，重复包不会
     * 重放 12 秒震颤），走出圈的玩家收 OFF 并从通知名单移除。客户端进圈/出圈
     * 由此收敛；界主本人也始终在收包范围内，死亡/出圈后滤镜一定能退掉。
     */
    private static void syncDomainAura(ServerLevel server, ActiveDomain domain) {
        double radiusSquared = RADIUS * RADIUS;
        AABB box = new AABB(domain.center.x - RADIUS, domain.center.y - RADIUS,
                domain.center.z - RADIUS, domain.center.x + RADIUS,
                domain.center.y + RADIUS, domain.center.z + RADIUS);
        java.util.List<ServerPlayer> inside = new java.util.ArrayList<>();
        for (ServerPlayer target : server.getEntitiesOfClass(ServerPlayer.class, box,
                Player::isAlive)) {
            double dx = target.getX() - domain.center.x;
            double dz = target.getZ() - domain.center.z;
            if (dx * dx + dz * dz <= radiusSquared) inside.add(target);
        }
        for (ServerPlayer target : inside) {
            JjkoaNetwork.sendDomainVisual(target, JjkoaNetwork.DOMAIN_VISUAL_ACTIVE);
            domain.visualNotified.add(target.getUUID());
        }
        // 名单里已经不在圈内的玩家（走出范围、死亡、换维度）：补一发 OFF 再摘除。
        domain.visualNotified.removeIf(target -> {
            if (inside.stream().anyMatch(player -> player.getUUID().equals(target))) return false;
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(target);
            if (player != null) JjkoaNetwork.sendDomainVisual(player, JjkoaNetwork.DOMAIN_VISUAL_OFF);
            return true;
        });
    }

    private static void applyPrecastSlow(ServerLevel server, Player owner, ChargingDomain charging) {
        Vec3 center = owner.position();
        AABB search = new AABB(center.x - PRECAST_RADIUS, center.y - PRECAST_RADIUS,
                center.z - PRECAST_RADIUS, center.x + PRECAST_RADIUS,
                center.y + PRECAST_RADIUS, center.z + PRECAST_RADIUS);
        double radiusSquared = PRECAST_RADIUS * PRECAST_RADIUS;
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, search,
                entity -> entity != owner && entity.isAlive())) {
            double dx = target.getX() - center.x;
            double dz = target.getZ() - center.z;
            if (dx * dx + dz * dz > radiusSquared) continue;
            // Slowness VI is the closest vanilla discrete modifier to 99%; the direct
            // velocity reduction also prevents AI knockback/motion from bypassing it.
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 6,
                    false, false, false));
            target.setDeltaMovement(target.getDeltaMovement().scale(0.01D));
            charging.slowedEntities.add(target.getUUID());
        }
    }

    private static void clearPrecastSlow(ServerLevel server, Player owner, ChargingDomain charging) {
        if (charging.slowedEntities.isEmpty()) return;
        Vec3 center = owner.position();
        AABB search = new AABB(center.x - PRECAST_RADIUS, center.y - PRECAST_RADIUS,
                center.z - PRECAST_RADIUS, center.x + PRECAST_RADIUS,
                center.y + PRECAST_RADIUS, center.z + PRECAST_RADIUS);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, search,
                entity -> charging.slowedEntities.contains(entity.getUUID()))) {
            target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        charging.slowedEntities.clear();
    }

    private static void damageEntities(ServerLevel server, Player owner, Vec3 center) {
        AABB search = new AABB(center.x - RADIUS, center.y - RADIUS, center.z - RADIUS,
                center.x + RADIUS, center.y + RADIUS, center.z + RADIUS);
        double radiusSquared = RADIUS * RADIUS;
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, search,
                entity -> entity != owner && entity.isAlive())) {
            double dx = target.getX() - center.x;
            double dz = target.getZ() - center.z;
            if (dx * dx + dz * dz > radiusSquared) continue;
            damageTarget(server, owner, target);
        }
    }

    private static void damageTarget(ServerLevel server, Player owner, LivingEntity target) {
        ItemStack held = owner.getMainHandItem();
        double heldDamage = Math.max(2.0D, owner.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float damage = (float) (heldDamage * DAMAGE_MULTIPLIER * JjkoaConfig.DAMAGE_MULTIPLIER);
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        if (target.hurt(server.damageSources().playerAttack(owner), damage)) {
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            int fire = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, held);
            if (fire > 0) target.setSecondsOnFire(fire * 4);
        }
        target.addEffect(new MobEffectInstance(GeneratedMod.SLICING_EFFECT.get(), 4, 0,
                false, false, false));
    }

    private static void spawnSlashStorm(ServerLevel server, ActiveDomain domain) {
        // 领域斩击的面片要对准界主：服务端把每刀的 yaw/pitch 指向界主眼睛，
        // 客户端任何角度看墨刀都是正面整幅，不会再出现侧对视线、刀身"缺一段"
        // 观感（此前 yaw/pitch 恒为 0，随机角度的面片大量侧对玩家而消失）。
        Entity owner = server.getEntity(domain.ownerEntityId);
        Vec3 viewerEye = owner != null ? owner.getEyePosition() : domain.center.add(0.0D, 1.5D, 0.0D);
        // 落点分布以神龛当前位置为锚：神龛是静止实体，取它的实时坐标。
        Vec3 shrinePos = domain.shrine != null && !domain.shrine.isRemoved()
                ? domain.shrine.position() : domain.center;
        double innerRadiusSquared = SHRINE_INNER_RADIUS * SHRINE_INNER_RADIUS;
        for (int i = 0; i < SLASHES_PER_STORM; i++) {
            double x;
            double z;
            if (server.random.nextDouble() < SHRINE_NEAR_CHANCE) {
                // 65%：神龛水平半径 32 格内均匀撒点。
                double angle = server.random.nextDouble() * Math.PI * 2.0D;
                double distance = Math.sqrt(server.random.nextDouble()) * SHRINE_INNER_RADIUS;
                x = shrinePos.x + Math.cos(angle) * distance;
                z = shrinePos.z + Math.sin(angle) * distance;
            } else {
                // 35%：领域范围（界主圆心 128 格圆）内、神龛 32 格圆外；
                // 落点仍压在神龛圆内时重采样，最多 12 次后按当前点放行。
                double angle = 0.0D;
                double distance = 0.0D;
                for (int attempt = 0; attempt < 12; attempt++) {
                    angle = server.random.nextDouble() * Math.PI * 2.0D;
                    distance = Math.sqrt(server.random.nextDouble()) * (RADIUS - 4.0D);
                    x = domain.center.x + Math.cos(angle) * distance;
                    z = domain.center.z + Math.sin(angle) * distance;
                    double dx = x - shrinePos.x;
                    double dz = z - shrinePos.z;
                    if (dx * dx + dz * dz >= innerRadiusSquared) break;
                }
                x = domain.center.x + Math.cos(angle) * distance;
                z = domain.center.z + Math.sin(angle) * distance;
            }
            // 高度规则按落点到神龛的实际水平距离分支：
            // 半径 32 格内——35% 直接落在神龛当前水平位置（无高度差），
            //                        65% 在神龛水平位置上方 6~12 格内浮动；
            // 半径 32 格外（领域范围内）——一律带高度差，
            //                        75% 概率在上方 0~60 格、25% 概率在下方 0~40 格内浮动。
            double dx = x - shrinePos.x;
            double dz = z - shrinePos.z;
            boolean insideShrineRadius = dx * dx + dz * dz < innerRadiusSquared;
            double y;
            if (insideShrineRadius) {
                if (server.random.nextDouble() < SHRINE_FLAT_CHANCE) {
                    y = shrinePos.y;
                } else {
                    y = shrinePos.y + SHRINE_FLOAT_MIN_ABOVE + server.random.nextDouble()
                            * (SHRINE_FLOAT_MAX_ABOVE - SHRINE_FLOAT_MIN_ABOVE);
                }
            } else {
                y = server.random.nextDouble() < STORM_ABOVE_CHANCE
                        ? shrinePos.y + server.random.nextDouble() * STORM_ABOVE_RANGE
                        : shrinePos.y - server.random.nextDouble() * STORM_BELOW_RANGE;
            }
            // 夹进建筑高度范围；未加载区块不投放实体，避免留下不 tick 的潜伏斩击。
            y = Mth.clamp(y, server.getMinBuildHeight() + 1, server.getMaxBuildHeight() - 1);
            BlockPos probe = BlockPos.containing(x, y, z);
            if (!server.hasChunkAt(probe)) continue;
            // 每刀长度在 15~65 格之间随机浮动（三种样式共用此区间，每发独立取随机；
            // 渲染端整幅贴斩击原画，按对角线换算边长）。
            int length = SLASH_STORM_MIN_LENGTH
                    + server.random.nextInt(SLASH_STORM_MAX_LENGTH - SLASH_STORM_MIN_LENGTH + 1);
            // 寿命 0.15 秒 = 3 tick：渲染端把这一刀所选样式的帧动画
            // （原斩击 1→2→3→4，红边/红色斩击 1→2→3）在这 0.15 秒内播完。
            DismantleProjectile slash = visualSlash(server, new Vec3(x, y, z),
                    server.random.nextFloat() * (float) Math.PI * 2.0F,
                    SLASH_STORM_LIFETIME_SECONDS, length);
            // 斩击风暴：每一发都从三种样式（原斩击/红边斩击/红色斩击）里随机挑一种生成播放。
            slash.setSlashStyle(server.random.nextInt(3));
            faceViewer(slash, new Vec3(x, y, z), viewerEye);
            server.addFreshEntity(slash);
        }
    }

    /** 让视觉斩击面片的法线指向观察者（与 setBurst 的朝向约定一致）。 */
    private static void faceViewer(DismantleProjectile slash, Vec3 position, Vec3 viewerEye) {
        Vec3 facing = viewerEye.subtract(position);
        if (facing.lengthSqr() < 1.0E-6D) return;
        Vec3 normalized = facing.normalize();
        slash.setYRot((float) (Math.atan2(normalized.z, normalized.x) * 180.0D / Math.PI) - 90.0F);
        slash.setXRot((float) (-Math.atan2(normalized.y,
                Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z)) * 180.0D / Math.PI));
    }

    private static void playDomainSweepSound(ServerLevel server, Vec3 center) {
        server.playSound(null, center.x, center.y, center.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F,
                0.75F + server.random.nextFloat() * 0.2F);
    }

    /**
     * 领域演出音（前摇/展开）：在指定位置播放一次，广播给附近能听见的玩家，
     * 并返回真正收到这段音乐的玩家 UUID 名单。
     * 判定半径取自 {@link SoundEvent#getRange(float)}——和 ServerLevel 内部广播
     * 用的半径是同一个值，所以名单就是"客户端真的在播这首曲子的那些人"。
     */
    private static Set<UUID> playDomainSound(ServerLevel server, Vec3 pos,
                                             SoundEvent sound, float volume) {
        server.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, 1.0F);
        Set<UUID> heard = new HashSet<>();
        double rangeSquared = (double) sound.getRange(volume) * sound.getRange(volume);
        for (ServerPlayer target : server.players()) {
            if (target.distanceToSqr(pos.x, pos.y, pos.z) <= rangeSquared) {
                heard.add(target.getUUID());
            }
        }
        return heard;
    }

    /**
     * 停播伏魔御厨子：音乐是服务端广播、客户端各自播放的实例，服务端手里没有句柄，
     * 所以关领域（或前摇中止）时只能补发一发 {@link ClientboundStopSoundPacket}，
     * 客户端 SoundManager 按"声音事件位置 + 音源"匹配，把还在响的那条掐掉。
     * 只发给上面记录过的听众，而不是整个维度全发：同维度另一个刚开启的领域
     * 用的是同一个声音事件，全发会把它一起掐掉。
     */
    private static void stopDomainMusic(ServerLevel server, Set<UUID> listeners,
                                       SoundEvent sound) {
        if (listeners.isEmpty()) return;
        ClientboundStopSoundPacket stop =
                new ClientboundStopSoundPacket(sound.getLocation(), SoundSource.PLAYERS);
        for (UUID listener : listeners) {
            ServerPlayer target = server.getServer().getPlayerList().getPlayer(listener);
            if (target != null) target.connection.send(stop);
        }
        listeners.clear();
    }

    private static DismantleProjectile visualSlash(ServerLevel server, Vec3 position, float angle,
                                                   double lifetimeSeconds, int length) {
        DismantleProjectile slash = new DismantleProjectile(GeneratedMod.DISMANTLE_PROJECTILE.get(), server);
        slash.setPos(position.x, position.y, position.z);
        slash.setVisualOnly(lifetimeSeconds, length);
        slash.setSlashAngle(angle);
        return slash;
    }

    /**
     * 边界环画在 128 格外的圆周上，而服务端的普通粒子广播只发给 32 格以内的玩家，
     * 所以界主（站在圆心）必须再走一次"逐玩家 + overrideLimiter"的通道（上限 512 格），
     * 否则半径放大后他自己反而看不见边界；环点本身仍照常广播，贴着边界站的其他人也能看到。
     */
    private static void sendBoundary(ServerLevel server, Vec3 center, ServerPlayer owner) {
        double ringY = center.y + 0.1D;
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = i * Math.PI * 2.0D / RING_POINTS;
            double x = center.x + Math.cos(angle) * RADIUS;
            double z = center.z + Math.sin(angle) * RADIUS;
            server.sendParticles(DOMAIN_RING, x, ringY, z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
            if (owner != null) {
                server.sendParticles(owner, DOMAIN_RING, true, x, ringY, z,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** Gradually clears loaded blocks inside the domain's horizontal circle, never below the shrine. */
    private static void destroyDomainTerrain(ServerLevel server, ActiveDomain domain) {
        if (!JjkoaConfig.TERRAIN_DAMAGE || domain.terrainComplete) return;

        int budget = domain.scanBudgetPerTick;
        int centerX = (int) Math.floor(domain.center.x);
        int centerZ = (int) Math.floor(domain.center.z);
        int maxY = server.getMaxBuildHeight();
        int radiusSquared = RADIUS_BLOCKS * RADIUS_BLOCKS;

        while (budget > 0 && domain.scanRadius <= RADIUS_BLOCKS) {
            if (domain.scanX > domain.scanRadius) {
                domain.scanRadius++;
                domain.scanX = -domain.scanRadius;
                domain.scanZ = -domain.scanRadius;
                domain.scanY = domain.startY;
                continue;
            }
            if (domain.scanZ > domain.scanRadius) {
                domain.scanX++;
                domain.scanZ = -domain.scanRadius;
                domain.scanY = domain.startY;
                continue;
            }

            int dx = domain.scanX;
            int dz = domain.scanZ;
            if (dx * dx + dz * dz > radiusSquared || domain.scanY >= maxY) {
                domain.scanZ++;
                domain.scanY = domain.startY;
                continue;
            }

            BlockPos pos = new BlockPos(centerX + dx, domain.scanY++, centerZ + dz);
            budget--;
            if (!server.hasChunkAt(pos)) continue;
            var state = server.getBlockState(pos);
            if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                server.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }

        if (domain.scanRadius > RADIUS_BLOCKS) domain.terrainComplete = true;
    }

    private static final class ChargingDomain {
        private final long startedAt;
        private long lastInputTick;
        private final Set<UUID> slowedEntities = new HashSet<>();
        /** 收到过"伏魔御厨子前摇"的玩家：前摇中止时按这份名单停播。 */
        private final Set<UUID> musicNotified = new HashSet<>();

        private ChargingDomain(long startedAt) {
            this.startedAt = startedAt;
            this.lastInputTick = startedAt;
        }
    }

    private static final class ActiveDomain {
        private final ServerLevel level;
        private final Vec3 center;
        private final int startY;
        private final int ownerEntityId;
        /** 方块破坏每 tick 的预算，按领域体积反推（详见 TERRAIN_SCAN_TARGET_TICKS）。 */
        private final int scanBudgetPerTick;
        private int scanRadius;
        private int scanX;
        private int scanZ;
        private int scanY;
        private boolean terrainComplete;
        private ShrineEntity shrine;
        /**
         * 收到过 ACTIVE 视觉包的玩家名单：关闭领域、玩家出圈或换维度时，
         * 按这份名单精确补发 OFF，避免"按圈广播"漏掉界主本人导致滤镜残留。
         */
        private final Set<UUID> visualNotified = new HashSet<>();
        /**
         * 收到过"伏魔御厨子"（展开音乐）的玩家名单：关闭领域时按这份名单停播，
         * 让 44 秒的音乐和领域同步结束。
         */
        private final Set<UUID> musicNotified = new HashSet<>();

        private ActiveDomain(ServerLevel level, Vec3 center, int ownerEntityId) {
            this.level = level;
            this.center = center;
            this.startY = (int) Math.floor(center.y);
            this.ownerEntityId = ownerEntityId;
            this.scanY = this.startY;
            this.scanBudgetPerTick = Mth.clamp((int) (Math.PI * RADIUS * RADIUS
                    * Math.max(1, level.getMaxBuildHeight() - this.startY)
                    / TERRAIN_SCAN_TARGET_TICKS), TERRAIN_BUDGET_MIN, TERRAIN_BUDGET_MAX);
        }

        private void spawnShrine(Vec3 position, float yaw) {
            shrine = new ShrineEntity(GeneratedMod.SHRINE_ENTITY.get(), level);
            shrine.setPos(position.x, position.y, position.z);
            // 嘴部在模型 -Z（实体朝向）面：yaw 与界主一致即面向玩家背部。
            shrine.setYRot(yaw);
            shrine.setXRot(0.0F);
            // 先登记再入世界：关闭领域时按这张表收摊（此刻领域还没进 ACTIVE_DOMAINS，
            // 只靠那张表才不会被当成"没人要的孤儿"漏掉或错杀）。
            LIVE_SHRINES.add(shrine);
            level.addFreshEntity(shrine);
        }

        private void removeShrine() {
            if (shrine != null) {
                LIVE_SHRINES.remove(shrine);
                shrine.discard();
                shrine = null;
            }
        }
    }
}
