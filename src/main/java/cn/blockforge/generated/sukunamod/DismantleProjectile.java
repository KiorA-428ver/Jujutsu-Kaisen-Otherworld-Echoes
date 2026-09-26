package cn.blockforge.generated.sukunamod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public final class DismantleProjectile extends Projectile {
    /** 普通斩击保持原速度。 */
    private static final double NORMAL_SPEED = 5.0D;
    private static final double MAX_DISTANCE = 150.0D;
    /**
     * 世界斩刀长：35 格。整幅 slash_frame_3 原画等比放大到这个刀长直接飞出
     * （不做任何拉伸加厚、破坏原画的操作），伤害框的 X/Z 跨度也按这个走
     * （伤害框 X35 × Y6 × Z35）。
     */
    public static final double WORLD_SLASH_BLADE_LENGTH = 35.0D;
    /**
     * 世界斩飞行距离：在 35 格刀长的基础上再 +85 格 = 120 格。蓄力完成后先在
     * 原位滞留，第 0.4 秒射出，飞满 120 格即消散。
     */
    public static final double WORLD_SLASH_FLIGHT_DISTANCE = WORLD_SLASH_BLADE_LENGTH + 85.0D;
    /** 脱手后 0.35 秒（前 7 tick）的滞留速度：每 tick 只挪 0.1 格。 */
    private static final double WORLD_SLASH_CREEP_SPEED = 0.1D;
    /** 第 0.4 秒（第 8 tick）起的射出速度：每秒 250 格 = 每 tick 12.5 格。 */
    private static final double WORLD_SLASH_DASH_SPEED = 12.5D;
    /** 切换到射出速度的那一刻：脱手后第 8 个 tick（0.4 秒）。 */
    private static final int WORLD_SLASH_DASH_TICK = 8;
    // 滞留 8 tick + 射满 120 格（约 10 tick）再留一点余量，到点即销毁。
    private static final int WORLD_SLASH_MAX_AGE = WORLD_SLASH_DASH_TICK
            + (int) Math.ceil(WORLD_SLASH_FLIGHT_DISTANCE / WORLD_SLASH_DASH_SPEED) + 2;
    private static final EntityDataAccessor<Float> SLASH_ANGLE = SynchedEntityData.defineId(
            DismantleProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> GRID_ATTACK = SynchedEntityData.defineId(
            DismantleProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WORLD_SLASH = SynchedEntityData.defineId(
            DismantleProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> VISUAL_ONLY = SynchedEntityData.defineId(
            DismantleProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> VISUAL_LENGTH = SynchedEntityData.defineId(
            DismantleProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> VISUAL_LIFETIME = SynchedEntityData.defineId(
            DismantleProjectile.class, EntityDataSerializers.FLOAT);
    /** 命中爆发：射线检测到实体/方块后在命中点播放的单刀贴图帧动画。 */
    private static final EntityDataAccessor<Boolean> BURST = SynchedEntityData.defineId(
            DismantleProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> BURST_SEED = SynchedEntityData.defineId(
            DismantleProjectile.class, EntityDataSerializers.FLOAT);
    /**
     * 视觉斩击的贴图样式：0=原斩击（slash_frame 1→2→3→4），
     * 1=红边斩击（red_edge_frame 1→2→3），2=红色斩击（red_frame 1→2→3）。
     * 连续解与领域斩击风暴每发随机取 0~2。
     */
    private static final EntityDataAccessor<Integer> SLASH_STYLE = SynchedEntityData.defineId(
            DismantleProjectile.class, EntityDataSerializers.INT);
    private float slashAngle;
    private double travelled;
    private int age;
    /** 世界斩是否已经进入"射出"段（滞留 0.35 秒后提速）；客户端与服务端各自按 tick 切换。 */
    private boolean worldSlashDashed;

    public DismantleProjectile(EntityType<? extends DismantleProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public void launch(Vec3 direction, float angle) {
        Vec3 normalized = direction.normalize();
        setDeltaMovement(normalized.scale(isWorldSlash() ? WORLD_SLASH_CREEP_SPEED : NORMAL_SPEED));
        slashAngle = angle;
        entityData.set(SLASH_ANGLE, angle);
        setYRot((float) (Math.atan2(normalized.z, normalized.x) * 180.0D / Math.PI) - 90.0F);
        setXRot((float) (-Math.atan2(normalized.y,
                Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z)) * 180.0D / Math.PI));
    }

    public float getSlashAngle() {
        return entityData.get(SLASH_ANGLE);
    }

    public void setSlashAngle(float angle) {
        slashAngle = angle;
        entityData.set(SLASH_ANGLE, angle);
    }

    public void setGridAttack(boolean value) {
        entityData.set(GRID_ATTACK, value);
    }

    public void setVisualOnly(int lifetime) {
        setVisualOnlyTicks(lifetime, 10);
    }

    public void setVisualOnlyTicks(int lifetime, int length) {
        entityData.set(VISUAL_ONLY, true);
        entityData.set(VISUAL_LENGTH, length);
        entityData.set(VISUAL_LIFETIME, (float) lifetime);
        setDeltaMovement(Vec3.ZERO);
    }

    public void setVisualOnly(double lifetimeSeconds, int length) {
        // 0.08 seconds is 1.6 game ticks. Store the exact duration in ticks so
        // the renderer can hide the slash between server entity updates.
        entityData.set(VISUAL_ONLY, true);
        entityData.set(VISUAL_LENGTH, length);
        entityData.set(VISUAL_LIFETIME, (float) (lifetimeSeconds * 20.0D));
        setDeltaMovement(Vec3.ZERO);
    }

    public int getVisualLength() {
        return entityData.get(VISUAL_LENGTH);
    }

    public boolean isVisualOnly() {
        return entityData.get(VISUAL_ONLY);
    }

    public float getVisualLifetime() {
        return entityData.get(VISUAL_LIFETIME);
    }

    public boolean shouldRenderVisual(float partialTick) {
        return age + partialTick < getVisualLifetime();
    }

    public boolean isGridAttack() {
        return entityData.get(GRID_ATTACK);
    }

    /**
     * 命中爆发模式：投射物停在命中点前，不移动，只按寿命播放单刀斩击贴图帧动画。
     * facing 是斩击贴图所在平面的法线（指向射手/命中面外侧一侧），贴图面片与该平面
     * 平行——命中方块时沿面法线摆正，避免斜射时斩击面一半埋进墙里只显示一半。
     * seed 是这一刀在贴图平面内的角度。
     */
    public void setBurst(Vec3 facing, double lifetimeSeconds, float seed) {
        entityData.set(VISUAL_ONLY, true);
        entityData.set(BURST, true);
        entityData.set(BURST_SEED, seed);
        entityData.set(VISUAL_LENGTH, 10);
        entityData.set(VISUAL_LIFETIME, (float) (lifetimeSeconds * 20.0D));
        setDeltaMovement(Vec3.ZERO);
        Vec3 normalized = facing.normalize();
        setYRot((float) (Math.atan2(normalized.z, normalized.x) * 180.0D / Math.PI) - 90.0F);
        setXRot((float) (-Math.atan2(normalized.y,
                Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z)) * 180.0D / Math.PI));
    }

    public boolean isBurst() {
        return entityData.get(BURST);
    }

    public float getBurstSeed() {
        return entityData.get(BURST_SEED);
    }

    /** 视觉斩击样式：0=原斩击四帧，1=红边斩击三帧，2=红色斩击三帧。 */
    public int getSlashStyle() {
        return entityData.get(SLASH_STYLE);
    }

    public void setSlashStyle(int style) {
        entityData.set(SLASH_STYLE, style);
    }

    public int getProjectileAge() {
        return age;
    }

    public void setWorldSlash(boolean value) {
        entityData.set(WORLD_SLASH, value);
    }

    public boolean isWorldSlash() {
        return entityData.get(WORLD_SLASH);
    }

    /**
     * 世界斩的时间轴：脱手后的前 0.35 秒只以每 tick 0.1 格的速度在原位附近滞留
     * （这两段黑白冲击帧就是给这 0.35 秒用的），到第 0.4 秒（第 8 个 tick）直接
     * 提速到每秒 250 格射出。速度切换按 tick 计算，客户端与服务端各自完成，
     * 双端时间轴一致，不会出现"服务端已飞出、客户端还在原地"的错位。
     */
    private void advanceWorldSlashPhase() {
        if (worldSlashDashed || age < WORLD_SLASH_DASH_TICK) return;
        worldSlashDashed = true;
        Vec3 direction = getDeltaMovement();
        if (direction.lengthSqr() < 1.0E-8D) direction = getLookAngle();
        if (direction.lengthSqr() < 1.0E-8D) return;
        setDeltaMovement(direction.normalize().scale(WORLD_SLASH_DASH_SPEED));
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (isVisualOnly()) {
            if (age >= Math.max(1.0F, getVisualLifetime())) discard();
            return;
        }
        if (isWorldSlash()) advanceWorldSlashPhase();
        Vec3 movement = getDeltaMovement();
        int maxAge = isWorldSlash() ? WORLD_SLASH_MAX_AGE : 40;
        double limit = isWorldSlash() ? WORLD_SLASH_FLIGHT_DISTANCE : MAX_DISTANCE;
        if (movement.lengthSqr() < 1.0E-8D || travelled >= limit || age > maxAge) {
            discard();
            return;
        }
        Vec3 from = position();
        Vec3 next = from.add(movement);
        if (!level().isClientSide) {
            // 世界斩不因方块射线命中而停止；它会沿整段路径持续扫掠并破坏
            // 35×6×35 范围内的方块，否则首个方块会让后续实体永远漏判。
            BlockHitResult blockHit = level().clip(new ClipContext(from, next,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 end = isWorldSlash() || blockHit.getType() == HitResult.Type.MISS
                    ? next : blockHit.getLocation();
            // 将 from 到 end 的整段路径交给技能处理，避免高速投射物跨过目标时漏判。
            DismantleSkill.damageAlong(this, from, end);
            if (!isWorldSlash() && blockHit.getType() != HitResult.Type.MISS) {
                discard();
                return;
            }
            setPos(end.x, end.y, end.z);
            travelled += movement.length();
        } else {
            setPos(next.x, next.y, next.z);
            travelled += movement.length();
        }
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SLASH_ANGLE, 0.0F);
        entityData.define(GRID_ATTACK, false);
        entityData.define(WORLD_SLASH, false);
        entityData.define(VISUAL_ONLY, false);
        entityData.define(VISUAL_LENGTH, 10);
        entityData.define(VISUAL_LIFETIME, 2.0F);
        entityData.define(BURST, false);
        entityData.define(BURST_SEED, 0.0F);
        entityData.define(SLASH_STYLE, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        slashAngle = tag.getFloat("SlashAngle");
        boolean gridAttack = tag.getBoolean("GridAttack");
        boolean worldSlash = tag.getBoolean("WorldSlash");
        entityData.set(SLASH_ANGLE, slashAngle);
        entityData.set(GRID_ATTACK, gridAttack);
        entityData.set(WORLD_SLASH, worldSlash);
        boolean visualOnly = tag.getBoolean("VisualOnly");
        entityData.set(VISUAL_ONLY, visualOnly);
        entityData.set(VISUAL_LENGTH, tag.contains("VisualLength") ? tag.getInt("VisualLength") : 10);
        entityData.set(VISUAL_LIFETIME, tag.contains("VisualLifetime")
                ? tag.getFloat("VisualLifetime") : 2.0F);
        entityData.set(BURST, tag.getBoolean("Burst"));
        entityData.set(BURST_SEED, tag.getFloat("BurstSeed"));
        entityData.set(SLASH_STYLE, tag.contains("SlashStyle") ? tag.getInt("SlashStyle") : 0);
        travelled = tag.getDouble("Travelled");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("SlashAngle", slashAngle);
        tag.putBoolean("GridAttack", isGridAttack());
        tag.putBoolean("WorldSlash", isWorldSlash());
        tag.putBoolean("VisualOnly", isVisualOnly());
        tag.putInt("VisualLength", getVisualLength());
        tag.putFloat("VisualLifetime", getVisualLifetime());
        tag.putBoolean("Burst", isBurst());
        tag.putFloat("BurstSeed", getBurstSeed());
        tag.putInt("SlashStyle", getSlashStyle());
        tag.putDouble("Travelled", travelled);
    }
}
