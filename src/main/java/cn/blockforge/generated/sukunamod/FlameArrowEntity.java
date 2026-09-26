package cn.blockforge.generated.sukunamod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** 灶·开火焰箭：蓄力时悬停在施术者面前，松手后沿视线飞行；触到实体/方块立即消失，由技能层延迟 2 秒引爆。 */
public final class FlameArrowEntity extends Projectile {
    private static final double SPEED = 2.4D;
    private static final int MAX_AGE = 100;
    private static final EntityDataAccessor<Boolean> LAUNCHED = SynchedEntityData.defineId(
            FlameArrowEntity.class, EntityDataSerializers.BOOLEAN);
    private int age;

    public FlameArrowEntity(EntityType<? extends FlameArrowEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    public boolean isLaunched() {
        return entityData.get(LAUNCHED);
    }

    public void setLaunched(boolean launched) {
        entityData.set(LAUNCHED, launched);
        noPhysics = !launched;
    }

    public void aimAt(Vec3 position, Vec3 direction) {
        setPos(position.x, position.y, position.z);
        setDeltaMovement(Vec3.ZERO);
        setRotationFromDirection(direction);
    }

    public void launch(Vec3 direction) {
        Vec3 normalized = direction.normalize();
        setRotationFromDirection(normalized);
        setDeltaMovement(normalized.scale(SPEED));
        age = 0; // 飞行寿命从发射起算，蓄力再久也不影响
        setLaunched(true);
    }

    private void setRotationFromDirection(Vec3 direction) {
        Vec3 normalized = direction.lengthSqr() < 1.0E-8D ? new Vec3(0, 0, 1) : direction.normalize();
        setYRot((float) (Math.atan2(normalized.z, normalized.x) * 180.0D / Math.PI) - 90.0F);
        setXRot((float) (-Math.atan2(normalized.y,
                Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z)) * 180.0D / Math.PI));
    }

    @Override
    public void tick() {
        super.tick();
        if (!isLaunched()) {
            // 蓄力悬停阶段不计时：否则手持过久，一松手 age 就已超过 MAX_AGE，箭矢会当场原地引爆。
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        age++;
        if (level().isClientSide) {
            setPos(position().add(getDeltaMovement()));
            return;
        }
        if (age > MAX_AGE) {
            detonateAfterDelay((ServerLevel) level());
            return;
        }
        Vec3 from = position();
        Vec3 movement = getDeltaMovement();
        Vec3 next = from.add(movement);
        BlockHitResult blockHit = level().clip(new ClipContext(from, next,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = blockHit.getType() == HitResult.Type.MISS ? next : blockHit.getLocation();
        LivingEntity target = level().getEntitiesOfClass(LivingEntity.class,
                new AABB(from, end).inflate(0.45D), entity -> entity != getOwner() && entity.isAlive())
                .stream().findFirst().orElse(null);
        if (target != null || blockHit.getType() != HitResult.Type.MISS) {
            setPos(target != null
                    ? target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                    : end);
            detonateAfterDelay((ServerLevel) level());
        } else {
            setPos(end.x, end.y, end.z);
        }
    }

    /** 箭矢在命中点直接消失，由 FlameArrowSkill 排 2 秒引信后原地爆炸。 */
    private void detonateAfterDelay(ServerLevel server) {
        FlameArrowSkill.scheduleImpact(server, this);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(LAUNCHED, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(LAUNCHED, tag.getBoolean("Launched"));
        age = tag.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Launched", isLaunched());
        tag.putInt("Age", age);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
