package cn.blockforge.generated.sukunamod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DismantleSkill {
    public static final int NORMAL = 0;
    public static final int STREAM = 1;
    public static final int BACKFLIP = 2;
    public static final int GRID = 3;
    public static final int GRID_STREAM = 4;
    private static final double RANGE = 10.0D;
    private static final double WIDTH = 0.5D;
    // 为高速投射物的两次服务器采样之间留出实体碰撞余量。
    private static final double PATH_SWEEP_MARGIN = 0.75D;
    private static final double BASE_DAMAGE_MULTIPLIER = 0.75D;
    private static final double GRID_DAMAGE_MULTIPLIER = 1.2D;
    private static final double WORLD_SLASH_DAMAGE_MULTIPLIER = 4.5D;
    // 世界斩的伤害框为 X35 × Y6 × Z35（半长 17.5 / 3 / 17.5），沿飞行路径扫掠。
    private static final double WORLD_SLASH_HALF_X = DismantleProjectile.WORLD_SLASH_BLADE_LENGTH * 0.5D;
    private static final double WORLD_SLASH_HALF_Y = 3.0D;
    private static final double WORLD_SLASH_HALF_Z = DismantleProjectile.WORLD_SLASH_BLADE_LENGTH * 0.5D;
    private static final double GRID_X = 9.0D;
    private static final double GRID_Y = 9.0D;
    private static final double GRID_Z = 9.0D;
    /** 射线投射的搜索长度：普通解/连续解/后撤解沿视线最远探测 96 格。 */
    private static final double RAY_RANGE = 96.0D;
    /** 命中点周围的伤害判定为 10×10×10 立方（破坏框的尺寸见下方 TRENCH_* 常量）。 */
    private static final double RAY_HIT_HALF = 5.0D;
    /** 破坏框的"宽10"：沿贴图刀刃方向在命中平面内 10 格长（半长 5）。 */
    private static final double TRENCH_HALF_LENGTH = 5.0D;
    /** 破坏框的"厚1"：在命中平面内垂直刀刃方向 1 格厚（半厚 0.5）。 */
    private static final double TRENCH_HALF_THICK = 0.5D;
    /** 破坏框的"深10"：从坑口面起，沿斩击飞行方向往地形里挤出 10 格。 */
    private static final double TRENCH_DEPTH = 10.0D;
    /**
     * 贴图原画的刀身沿画布左上→右下对角线（平面内 −45°），burst seed 只是把整幅
     * 原画绕命中面再转一个角度，所以破坏框的刀刃朝向取 seed − 45°：后撤解的
     * 0°/90° 两记贴图落在 ±45° 两条对角线上，对应的两道破坏框在命中平面内
     * 交叉成 X，坑的形状与贴图上看到的 X 完全一致。
     */
    private static final double TRENCH_TEXTURE_DIAGONAL_OFFSET = Math.PI / 4.0D;
    /** 命中动画寿命（秒）：单刀贴图帧动画 1→2→3→4，总时长 5 tick = 0.25 秒内播完。 */
    private static final double BURST_LIFETIME_SECONDS = 0.25D;
    /** 连续解的贴图在命中点周围 2×2 方块范围内随机浮动出现。 */
    private static final double STREAM_SPREAD = 1.0D;
    private DismantleSkill() { }

    public static void cast(Player player, int mode) {
        if (!CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)
                || !SkillCooldowns.isReady(player, SkillCooldowns.DISMANTLE)) return;
        boolean streaming = mode == STREAM || mode == GRID_STREAM;
        if (streaming) {
            if (!CursedEnergy.has(player, 10)) return;
            if (!CursedEnergy.tryConsumeTimed(player, "dismantle_" + mode, 10, 2)) return;
        } else {
            if (!CursedEnergy.has(player, 10)
                    || !SkillCooldowns.isReady(player, SkillCooldowns.DISMANTLE)) return;
            if (!SkillCooldowns.tryUse(player, SkillCooldowns.DISMANTLE,
                    SkillCooldowns.DISMANTLE_COOLDOWN_TICKS)
                    || !CursedEnergy.tryConsume(player, 10)) return;
        }
        playCastSound(player, mode);
        if (mode == BACKFLIP) {
            Vec3 look = player.getLookAngle().normalize();
            Vec3 backward = new Vec3(-look.x, 0.0D, -look.z);
            if (backward.lengthSqr() > 1.0E-6D) backward = backward.normalize();
            player.setDeltaMovement(backward.x, 0.8D, backward.z);
            player.hurtMarked = true;
            // 后撤解：两记交叉斜斩，在命中点叠成 X 形。贴图原画的刀身本就沿
            // 左上→右下对角线，所以种子角取 0 与 90°（而非摆平条带时代的 ±45°）。
            // 随贴图各发射一记隐形破坏斩击：两记的刀刃落在命中平面 ±45° 对角线上，
            // 切出的两个 10×1×10 破坏框交叉成 X，坑形与贴图一致。
            rayCast(player, new float[] { 0.0F, (float) (Math.PI / 2.0D) }, false);
            return;
        }
        if (mode == GRID || mode == GRID_STREAM) {
            fireGrid(player);
            return;
        }
        // 普通解与连续解：不可视射线，命中实体/方块才在命中点播放单刀贴图帧动画；
        // 连续解的贴图位置在命中点周围 2×2 方块内随机浮动。
        rayCast(player, new float[] { (float) (player.getRandom().nextDouble() * Math.PI * 2.0D) },
                mode == STREAM);
    }

    private static void playCastSound(Player player, int mode) {
        if (!(player.level() instanceof ServerLevel server)) return;
        if (mode == GRID || mode == GRID_STREAM) {
            server.playSound(null, player.getX(), player.getY(), player.getZ(),
                    GeneratedMod.GRID_SLASH_SOUND.get(), SoundSource.PLAYERS, 1.0F,
                    0.95F + player.getRandom().nextFloat() * 0.1F);
            return;
        }
        // 连续解：按要求不再播放宿傩斩击音效（原先的客户端循环音也已移除），
        // 施放瞬间完全静音。
        if (mode == STREAM) return;
        // 普通解与后撤解：施放瞬间播放宿傩斩击音效，周围玩家都能听到。
        server.playSound(null, player.getX(), player.getY(), player.getZ(),
                GeneratedMod.SUKUNA_SLASH_SOUND.get(), SoundSource.PLAYERS, 1.1F,
                0.97F + player.getRandom().nextFloat() * 0.07F);
    }

    /**
     * 射线投射斩：沿视线一次性探测最远的 96 格，实体与方块谁先挡住射线就以谁为准。
     * 飞行过程完全不可视、不使用任何粒子；命中时按 seeds 里的每个平面内角度各播
     * 一记单刀斩击贴图帧动画 1→2→3→4（共 0.25 秒内播完，四张原画整张
     * 直贴、笔触原样保留）后消失——后撤解给 0°/90° 两个种子，
     * 两刀同点交叉叠成 X 形，伤害只按一条射线结算一次。
     * 贴图平面与命中面平行并整体向射手方向前置
     * （方块前 0.5 格、实体前 1 格），保证斩击不会被埋进墙里只显示一半；
     * 连续解的贴图在命中面内 2×2 方块范围内随机浮动出现。命中点 10×10×10
     * 范围内的每个目标受到一次伤害。
     * <p>
     * 方块破坏改由"隐形破坏斩击"（TrenchSlashEntity）执行：每记贴图斩同步发射
     * 一记只负责破坏的高速斩击（每 tick 64 格、全程不可见），它沿同一条射线飞到
     * 命中点后撞到方块即消失，并在消失的那一刻按贴图的对齐数据切出破坏框——
     * 宽 10（沿贴图刀刃方向）、厚 1（命中平面内垂直刀刃）、深 10（沿斩击飞行方向
     * 往地形里挤出）。砍地面就是 10×1 口往下 10 格深的坑，砍墙面就是打进墙里
     * 10 格的 10×1 长缝；坑随贴图出现（1~2 tick 内、帧动画播放期间落地），
     * 位置和朝向跟着贴图浮动/旋转。后撤解的两记破坏斩击在命中平面内落在 ±45°
     * 对角线上、交叉成 X，斩出的坑与贴图上看到的 X 一致。破坏仅在命中那一刻
     * /jjkoa terrain 开启时生效（与领域共用同一个开关）。
     */
    private static void rayCast(Player player, float[] seeds, boolean spread) {
        if (!(player.level() instanceof ServerLevel server)) return;
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        if (direction.lengthSqr() < 1.0E-8D) return;
        direction = direction.normalize();
        Vec3 end = eye.add(direction.scale(RAY_RANGE));

        Vec3 hitPoint = null;
        // 斩击贴图平面的法线：指向射手一侧。方块命中用受击面的外法线，
        // 实体命中用射线反方向；贴图沿该法线前置，避免斜射时一半埋进命中面。
        Vec3 facing = null;
        boolean blockHitFlag = false;
        double bestDistanceSq = Double.MAX_VALUE;
        BlockHitResult blockHit = server.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            hitPoint = blockHit.getLocation();
            facing = new Vec3(blockHit.getDirection().getStepX(),
                    blockHit.getDirection().getStepY(), blockHit.getDirection().getStepZ());
            blockHitFlag = true;
            bestDistanceSq = eye.distanceToSqr(hitPoint);
        }
        // 实体射线：在整条射线的宽松包围盒内做精确的 AABB 射线求交，取最近命中。
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, end).inflate(1.0D),
                entity -> entity != player && entity.isAlive() && entity.canBeHitByProjectile())) {
            Optional<Vec3> clip = target.getBoundingBox().inflate(0.25D).clip(eye, end);
            if (clip.isEmpty()) continue;
            double distanceSq = eye.distanceToSqr(clip.get());
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                hitPoint = clip.get();
                facing = direction.scale(-1.0D);
                blockHitFlag = false;
            }
        }
        // 没有命中任何实体/方块：斩击飞出射程，全程不可视、无任何特效。
        if (hitPoint == null || facing == null) return;

        // 命中面内的切向基：连续解的 2×2 浮动只沿这两个切向进行（不会把贴图甩进
        // 墙里造成遮挡），破坏槽的刀刃方向也由它们张成（贴图就在这个平面内自转）。
        // 这套基必须逐项等于客户端贴图面片的世界坐标系：渲染器用 YP(180−yaw)·XP(pitch)
        // 旋转面片（yaw/pitch 即 setBurst 的 atan2 换算），面片局部轴因此是
        // +X = normalize(facing×up)（facing 竖直时按客户端约定退化为 (0,0,1)）、
        // +Z = (−fx, fy, −fz)（facing 的水平镜像——射手一侧看到的其实是面片背面）、
        // +Y = +Z×+X。旧写法 tangent2 = facing×tangent1 恰好与面片 +Y 反号：
        // 刀刃角度被水平轴镜像，正对墙面斩出的坑与贴图互为镜像（贴图 "/" 坑成 "\"），
        // 只有正视上下时两套基碰巧给出同一条线——这就是"下方与上方正常、
        // 正对前方相反"的根源。现在按面片真实基构造，任意命中方向坑都与贴图同线。
        Vec3 quadNormal = new Vec3(-facing.x, facing.y, -facing.z).normalize();
        Vec3 tangent1 = facing.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (tangent1.lengthSqr() < 1.0E-6D) {
            tangent1 = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            tangent1 = tangent1.normalize();
        }
        Vec3 tangent2 = quadNormal.cross(tangent1).normalize();
        double jitter1 = spread ? (server.random.nextDouble() * 2.0D - 1.0D) * STREAM_SPREAD : 0.0D;
        double jitter2 = spread ? (server.random.nextDouble() * 2.0D - 1.0D) * STREAM_SPREAD : 0.0D;
        Vec3 inPlane = tangent1.scale(jitter1).add(tangent2.scale(jitter2));
        // 贴图中心沿法线向射手方向前置：方块面 0.5 格（刚好离开墙面不 Z-fighting），
        // 实体 1 格（躲开大型实体朝射手一侧鼓出的身体）；连续解再叠加命中面内浮动。
        Vec3 visualPoint = hitPoint.add(facing.scale(blockHitFlag ? 0.5D : 1.0D)).add(inPlane);
        // 每个种子角各生成一记贴图斩，共用同一命中点与平面法线：
        // 后撤解的两个 ±45° 种子在同一中心交叉叠成 X 形。
        // 连续解：每发斩击从三种样式（原斩击/红边斩击/红色斩击）里随机挑一种播放。
        for (float seed : seeds) {
            DismantleProjectile burst = new DismantleProjectile(
                    GeneratedMod.DISMANTLE_PROJECTILE.get(), server);
            burst.setOwner(player);
            burst.setPos(visualPoint.x, visualPoint.y, visualPoint.z);
            burst.setBurst(facing, BURST_LIFETIME_SECONDS, seed);
            if (spread) burst.setSlashStyle(server.random.nextInt(3));
            server.addFreshEntity(burst);
        }

        AABB area = new AABB(hitPoint.x - RAY_HIT_HALF, hitPoint.y - RAY_HIT_HALF,
                hitPoint.z - RAY_HIT_HALF, hitPoint.x + RAY_HIT_HALF,
                hitPoint.y + RAY_HIT_HALF, hitPoint.z + RAY_HIT_HALF);
        // 方块破坏交给隐形破坏斩击：贴图出现几刀，就发射几记只负责破坏的高速斩击。
        // 每一记携带该刀贴图的对齐数据——坑口取命中点+面内浮动（连续解的 2×2
        // 贴图浮动带着坑一起漂），刀刃朝向取贴图原画刀身所在平面的实际方向
        // （seed − 45°，见 TRENCH_TEXTURE_DIAGONAL_OFFSET），挤出方向取打进地形的一侧
        // （方块命中沿受击面法线往里，实体命中沿飞行方向继续往前）。它们飞到
        // 命中点撞到方块才消失并切出 10宽1厚10深 的破坏框，与贴图帧动画同步落地。
        Vec3 trenchMouth = hitPoint.add(inPlane);
        Vec3 inward = blockHitFlag ? facing.scale(-1.0D) : direction;
        for (float seed : seeds) {
            double inPlaneAngle = seed - TRENCH_TEXTURE_DIAGONAL_OFFSET;
            Vec3 blade = tangent1.scale(Math.cos(inPlaneAngle))
                    .add(tangent2.scale(Math.sin(inPlaneAngle)));
            TrenchSlashEntity slash = new TrenchSlashEntity(
                    GeneratedMod.TRENCH_SLASH.get(), server);
            slash.setOwner(player);
            slash.configure(eye, direction, Math.sqrt(bestDistanceSq),
                    trenchMouth, blade, inward);
            server.addFreshEntity(slash);
        }
        ItemStack held = player.getMainHandItem();
        double heldDamage = Math.max(2.0D, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        double damage = heldDamage * BASE_DAMAGE_MULTIPLIER * JjkoaConfig.DAMAGE_MULTIPLIER;
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && entity.isAlive())) {
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            boolean damaged = target.hurt(server.damageSources().playerAttack(player), (float) damage);
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            if (damaged) {
                int fire = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, held);
                if (fire > 0) target.setSecondsOnFire(fire * 4);
            }
        }
        // 命中音效只保留给连续解（spread 即连续解）：普通解与后撤解按要求
        // 去掉横扫之刃音效，它们的听觉反馈就是施放瞬间的那记宿傩斩击。
        if (spread) {
            server.playSound(null, hitPoint.x, hitPoint.y, hitPoint.z,
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F,
                    0.95F + server.random.nextFloat() * 0.1F);
        }
    }

    private static void fireGrid(Player player) {
        Level level = player.level();
        DismantleProjectile projectile = new DismantleProjectile(GeneratedMod.DISMANTLE_PROJECTILE.get(), level);
        projectile.setOwner(player);
        Vec3 origin = new Vec3(player.getX(), player.getEyeY() - 0.15D, player.getZ());
        projectile.setPos(origin.x, origin.y, origin.z);
        projectile.setGridAttack(true);
        projectile.launch(player.getLookAngle(), 0.0F);
        level.addFreshEntity(projectile);
    }

    public static void damageAlong(DismantleProjectile projectile, Vec3 center) {
        damageAlong(projectile, center, center);
    }

    /**
     * 扫描投射物本 tick 从 start 到 end 的整段路径，而不是只检查终点。
     * 普通“解”每 tick 移动 5 格，单点检测会在目标位于两次检测之间时漏判。
     */
    public static void damageAlong(DismantleProjectile projectile, Vec3 start, Vec3 end) {
        if (!(projectile.level() instanceof ServerLevel server)) return;
        Entity owner = projectile.getOwner();
        if (!(owner instanceof Player player)) return;
        // 世界斩的实际攻击框是以斩击中心为中心的 X35 × Y6 × Z35 立方体，且随飞行路径扫掠。
        AABB area = projectile.isWorldSlash() ? sweptWorldSlashArea(start, end)
                : (projectile.isGridAttack() ? sweptGridArea(projectile, start, end)
                : sweptSlashArea(projectile, start, end));
        // 世界斩也必须服从 /jjkoa terrain <true|false>，不能因为技能类型而绕过开关。
        if (JjkoaConfig.TERRAIN_DAMAGE) destroyBlocksInArea(server, area);
        Set<UUID> alreadyHit = projectile.getPersistentData().contains("HitEntities")
                ? readHits(projectile.getPersistentData().getString("HitEntities")) : new HashSet<>();
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && entity.isAlive()
                        && (projectile.isWorldSlash() || !alreadyHit.contains(entity.getUUID())
                        )
                        && (!projectile.isGridAttack() || insideGridArea(projectile, start, entity)
                        || insideGridArea(projectile, end, entity)))) {
            // 高速世界斩按“路径扫掠框”判定，而不是依赖投射物终点碰撞。
            // getEntitiesOfClass 已按实体包围盒完成相交筛选，这里不再追加边界判断，
            // 避免目标恰好贴着 X35 × Y6 × Z35 攻击框边缘时被浮点误差漏掉。
            ItemStack held = player.getMainHandItem();
            double heldDamage = Math.max(2.0D, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
            double multiplier = projectile.isWorldSlash() ? WORLD_SLASH_DAMAGE_MULTIPLIER
                    : (projectile.isGridAttack() ? GRID_DAMAGE_MULTIPLIER : BASE_DAMAGE_MULTIPLIER);
            double damage = heldDamage * multiplier * JjkoaConfig.DAMAGE_MULTIPLIER;
            boolean damaged;
            if (projectile.isWorldSlash()) {
                damaged = dealWorldSlashTrueDamage(server, player, target, damage);
            } else {
                target.invulnerableTime = 0;
                target.hurtTime = 0;
                damaged = target.hurt(server.damageSources().playerAttack(player), (float) damage);
                target.invulnerableTime = 0;
                target.hurtTime = 0;
            }
            if (damaged) {
                int fire = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, held);
                if (fire > 0) target.setSecondsOnFire(fire * 4);
                alreadyHit.add(target.getUUID());
            }
        }
        projectile.getPersistentData().putString("HitEntities", writeHits(alreadyHit));
    }

    /**
     * 世界斩优先走原版 hurt 入口以保留击杀归属；当实体或伤害事件拒绝这次攻击、
     * 且生命值没有实际下降时，再使用生命值回退，避免高速飞行过程中丢失伤害。
     */
    private static boolean dealWorldSlashTrueDamage(ServerLevel server, Player attacker,
                                                     LivingEntity target, double amount) {
        if (!(amount > 0.0D) || Double.isNaN(amount) || !target.isAlive()) return false;
        float oldHealth = target.getHealth();
        float damage = amount >= Float.MAX_VALUE ? Float.MAX_VALUE : (float) amount;
        float newHealth = Math.max(0.0F, oldHealth - damage);
        if (newHealth >= oldHealth) return false;

        target.setLastHurtByPlayer(attacker);
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        // 先走原版伤害入口，让 Forge/实体自身完成生命值同步、受击动画和事件归属；
        // 清零无敌帧后 1.20.1 的伤害入口不会再因前一次攻击吞掉世界斩。
        boolean hurt = target.hurt(server.damageSources().playerAttack(attacker), damage);
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        // 不能只看 hurt 的返回值：某些实体/Forge 事件会返回 true，但把最终伤害改成 0。
        // 只有确认生命值确实下降（或实体已死亡）才结束本次判定。
        if (!target.isAlive() || target.getHealth() < oldHealth) return true;

        // 某些实体（例如坚守者）或外部伤害事件可能拒绝 playerAttack，
        // 入口未造成生命值变化时才使用生命值回退，保证世界斩不会出现“飞过去但没伤害”。
        float fallbackHealth = Math.max(0.0F, oldHealth - damage);
        target.setHealth(fallbackHealth);
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        if (fallbackHealth <= 0.0F) {
            target.die(server.damageSources().playerAttack(attacker));
        } else {
            server.broadcastEntityEvent(target, (byte) 2);
        }
        return true;
    }

    /** 世界斩的实际伤害与破坏框：以斩击自身为中心的 X35 × Y6 × Z35 立方（随飞行路径扫掠）。 */
    private static AABB sweptWorldSlashArea(Vec3 start, Vec3 end) {
        return new AABB(Math.min(start.x, end.x) - WORLD_SLASH_HALF_X,
                Math.min(start.y, end.y) - WORLD_SLASH_HALF_Y,
                Math.min(start.z, end.z) - WORLD_SLASH_HALF_Z,
                Math.max(start.x, end.x) + WORLD_SLASH_HALF_X,
                Math.max(start.y, end.y) + WORLD_SLASH_HALF_Y,
                Math.max(start.z, end.z) + WORLD_SLASH_HALF_Z);
    }

    private static AABB sweptSlashArea(DismantleProjectile projectile, Vec3 start, Vec3 end) {
        AABB first = slashArea(projectile, start);
        AABB last = slashArea(projectile, end);
        return new AABB(Math.min(first.minX, last.minX), Math.min(first.minY, last.minY),
                Math.min(first.minZ, last.minZ), Math.max(first.maxX, last.maxX),
                Math.max(first.maxY, last.maxY), Math.max(first.maxZ, last.maxZ))
                .inflate(PATH_SWEEP_MARGIN);
    }

    private static AABB sweptGridArea(DismantleProjectile projectile, Vec3 start, Vec3 end) {
        AABB first = gridArea(projectile, start);
        AABB last = gridArea(projectile, end);
        return new AABB(Math.min(first.minX, last.minX), Math.min(first.minY, last.minY),
                Math.min(first.minZ, last.minZ), Math.max(first.maxX, last.maxX),
                Math.max(first.maxY, last.maxY), Math.max(first.maxZ, last.maxZ));
    }

    private static AABB slashArea(DismantleProjectile projectile, Vec3 center) {
        Vec3 direction = projectile.getDeltaMovement().normalize();
        Vec3 right = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-6D) right = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        right = right.normalize();
        Vec3 up = direction.cross(right).normalize();
        double angle = projectile.getSlashAngle();
        Vec3 slashAxis = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).normalize();
        double halfLength = RANGE * 0.5D - WIDTH;
        Vec3 first = center.subtract(slashAxis.scale(halfLength));
        Vec3 last = center.add(slashAxis.scale(halfLength));
        return new AABB(first, last).inflate(WIDTH, WIDTH, WIDTH);
    }

    /** Returns the requested world-axis-aligned X9 Y9 Z1 grid volume. */
    private static AABB gridArea(DismantleProjectile projectile, Vec3 center) {
        return new AABB(center.x - GRID_X * 0.5D, center.y - GRID_Y * 0.5D,
                center.z - GRID_Z * 0.5D, center.x + GRID_X * 0.5D,
                center.y + GRID_Y * 0.5D, center.z + GRID_Z * 0.5D);
    }

    private static boolean insideGridArea(DismantleProjectile projectile, Vec3 center, LivingEntity target) {
        AABB targetBox = target.getBoundingBox();
        return targetBox.maxX >= center.x - GRID_X * 0.5D
                && targetBox.minX <= center.x + GRID_X * 0.5D
                && targetBox.maxY >= center.y - GRID_Y * 0.5D
                && targetBox.minY <= center.y + GRID_Y * 0.5D
                && targetBox.maxZ >= center.z - GRID_Z * 0.5D
                && targetBox.minZ <= center.z + GRID_Z * 0.5D;
    }

    private static void destroyBlocksInArea(ServerLevel level, AABB area) {
        int minX = (int) Math.floor(area.minX);
        int maxX = (int) Math.ceil(area.maxX) - 1;
        int minY = (int) Math.floor(area.minY);
        int maxY = (int) Math.ceil(area.maxY) - 1;
        int minZ = (int) Math.floor(area.minZ);
        int maxZ = (int) Math.ceil(area.maxZ) - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    // Replace directly with air: no drops and no full block-destruction
                    // callback/neighbour update work for every block in the slash box.
                    if (!level.getBlockState(pos).isAir()
                            && !level.getBlockState(pos).is(Blocks.BEDROCK)) level.setBlock(pos,
                            Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /**
     * 一记隐形破坏斩击切出的破坏框：宽 10（沿贴图刀刃方向 10 格长）、厚 1（在命中
     * 平面内垂直刀刃方向 1 格）、深 10（从被切中的面起，沿斩击前进方向往地形里
     * 挤出 10 格）。{@code mouth} 是坑口中心（贴图的命中点+面内浮动），
     * {@code blade} 是该记贴图的刀刃朝向，{@code inward} 是挤出方向。
     * <p>
     * 同一套几何在不同命中面上自然给出用户要的两种坑形：砍地面时 inward 竖直向下，
     * 就是 10×1 口往下 10 格深的槽；砍墙面时 inward 水平朝墙内，就是打进墙里 10 格、
     * 长 10 厚 1 的刀缝；后撤解两记破坏斩击落在命中平面 ±45° 对角线上，截断面
     * 交叉成 X，与贴图上的 X 一致。逐方块取中心点做斩击局部坐标 (blade, 厚向, inward)
     * 的投影判定，刀身转多少度，破坏框就跟着转多少度，始终是 10 宽 × 1 厚 × 10 深。
     */
    static void applyTrenchCut(ServerLevel level, Vec3 mouth, Vec3 blade, Vec3 inward) {
        Vec3 b = blade.normalize();
        Vec3 i = inward.normalize();
        Vec3 w = i.cross(b);
        // 刀刃与挤出方向理论上垂直（blade 在命中平面内，inward 是平面法线一侧）；
        // 万一退化成平行（脏数据），这一刀就不切，避免厚向算不出基向量。
        if (w.lengthSqr() < 1.0E-6D) return;
        w = w.normalize();
        double halfDepth = TRENCH_DEPTH * 0.5D;
        Vec3 center = mouth.add(i.scale(halfDepth));
        double rx = TRENCH_HALF_LENGTH * Math.abs(b.x) + TRENCH_HALF_THICK * Math.abs(w.x)
                + halfDepth * Math.abs(i.x);
        double ry = TRENCH_HALF_LENGTH * Math.abs(b.y) + TRENCH_HALF_THICK * Math.abs(w.y)
                + halfDepth * Math.abs(i.y);
        double rz = TRENCH_HALF_LENGTH * Math.abs(b.z) + TRENCH_HALF_THICK * Math.abs(w.z)
                + halfDepth * Math.abs(i.z);
        int minX = (int) Math.floor(center.x - rx);
        int maxX = (int) Math.ceil(center.x + rx);
        int minY = (int) Math.floor(center.y - ry);
        int maxY = (int) Math.ceil(center.y + ry);
        int minZ = (int) Math.floor(center.z - rz);
        int maxZ = (int) Math.ceil(center.z + rz);
        for (int y = minY; y <= maxY; y++) {
            double dy = y + 0.5D - mouth.y;
            for (int x = minX; x <= maxX; x++) {
                double dx = x + 0.5D - mouth.x;
                for (int z = minZ; z <= maxZ; z++) {
                    double dz = z + 0.5D - mouth.z;
                    // 局部坐标：沿刀刃 ±5（宽10）、沿厚向 ±0.5（厚1）、沿 inward 0~10（深10）。
                    if (Math.abs(dx * b.x + dy * b.y + dz * b.z) > TRENCH_HALF_LENGTH) continue;
                    if (Math.abs(dx * w.x + dy * w.y + dz * w.z) > TRENCH_HALF_THICK) continue;
                    double n = dx * i.x + dy * i.y + dz * i.z;
                    if (n < 0.0D || n > TRENCH_DEPTH) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    // 96 格射程的末端可能已经超出玩家加载中的视野，这里跳过未加载的
                    // 位置，避免一次斩击把周围的区块凭空生成出来。
                    if (!level.isLoaded(pos)) continue;
                    // Replace directly with air: no drops and no full block-destruction
                    // callback/neighbour update work for every block in the trench.
                    if (!level.getBlockState(pos).isAir()
                            && !level.getBlockState(pos).is(Blocks.BEDROCK)) level.setBlock(pos,
                            Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static Set<UUID> readHits(String value) {
        Set<UUID> hits = new HashSet<>();
        for (String part : value.split(",")) {
            try { if (!part.isEmpty()) hits.add(UUID.fromString(part)); } catch (IllegalArgumentException ignored) { }
        }
        return hits;
    }

    private static String writeHits(Set<UUID> hits) {
        StringBuilder result = new StringBuilder();
        for (UUID hit : hits) {
            if (result.length() > 0) result.append(',');
            result.append(hit);
        }
        return result.toString();
    }
}
