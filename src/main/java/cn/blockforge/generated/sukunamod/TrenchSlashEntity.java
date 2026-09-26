package cn.blockforge.generated.sukunamod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * 隐形破坏斩击：普通解/后撤解/连续解在射线投射命中贴图的同时，额外沿同一条射线
 * 发射的高速斩击。它没有任何渲染（TrenchSlashRenderer 恒不绘制），只负责方块破坏：
 * 每 tick 前进 64 格，撞到方块（实体命中时飞到射线命中点）即消失，并在消失的这一刻
 * 开始破坏方块——坑的位置/朝向携带的就是那一记贴图的对齐数据（命中点+连续解的
 * 2×2 浮动+贴图平面内的刀刃角度），所以斩出的方块破坏天然与贴图同步。
 * 是否真的破坏由它命中那一刻的 /jjkoa terrain 开关决定。
 */
public final class TrenchSlashEntity extends Projectile {
    /** 破坏斩击速度：每 tick 64 格，射线最远 96 格时 2 tick 内抵达命中点。 */
    private static final double SPEED = 64.0D;
    /** 安全上限：远超射程所需 tick 数仍未命中就自毁（不应发生，只兜底）。 */
    private static final int MAX_AGE = 6;
    /** 发射点（射手眼部）：用来按"离出发点飞了多远"判断是否到达射线命中点。 */
    private Vec3 launchOrigin = Vec3.ZERO;
    /** 坑口中心：贴图的命中点（连续解已叠加 2×2 面内浮动）。 */
    private Vec3 mouth;
    /** 刀刃长边方向：该记贴图斩在命中平面内的朝向（seed − 45°）。 */
    private Vec3 blade = Vec3.ZERO;
    /** 挤出方向：方块命中为命中面法线的反方向（打进墙里），实体命中为飞行方向。 */
    private Vec3 inward = Vec3.ZERO;
    /** 触发破坏的距离：眼部到射线命中点的距离。 */
    private double targetDistance;
    private int age;

    public TrenchSlashEntity(EntityType<? extends TrenchSlashEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    /** 服务端出手时一次性写入对齐数据；这些数据只参与命中判定，不参与任何渲染。 */
    public void configure(Vec3 origin, Vec3 direction, double targetDistance,
                          Vec3 mouth, Vec3 blade, Vec3 inward) {
        launchOrigin = origin;
        this.targetDistance = targetDistance;
        this.mouth = mouth;
        this.blade = blade;
        this.inward = inward;
        setPos(origin.x, origin.y, origin.z);
        setDeltaMovement(direction.normalize().scale(SPEED));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || mouth == null) return;
        age++;
        if (age > MAX_AGE) {
            discard();
            return;
        }
        Vec3 from = position();
        Vec3 next = from.add(getDeltaMovement());
        // 与射线投射同一条件：本 tick 的整段位移跨过命中点即视为到达。
        boolean reachedTarget = launchOrigin.distanceToSqr(next) >= targetDistance * targetDistance;
        BlockHitResult blockHit = level().clip(new ClipContext(from, next,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (!reachedTarget && blockHit.getType() == HitResult.Type.MISS) {
            setPos(next.x, next.y, next.z);
            return;
        }
        // 撞到方块（或飞到命中点）：斩击消失；开启方块破坏后，此刻按贴图对齐的位置
        // 切出 10 宽 × 1 厚 × 10 深的破坏框。
        if (JjkoaConfig.TERRAIN_DAMAGE && level() instanceof ServerLevel server) {
            DismantleSkill.applyTrenchCut(server, mouth, blade, inward);
        }
        discard();
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        launchOrigin = readVec(tag, "LaunchOrigin");
        mouth = hasMouth(tag) ? readVec(tag, "Mouth") : null;
        blade = readVec(tag, "Blade");
        inward = readVec(tag, "Inward");
        targetDistance = tag.getDouble("TargetDistance");
        age = tag.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        writeVec(tag, "LaunchOrigin", launchOrigin);
        if (mouth != null) writeVec(tag, "Mouth", mouth);
        writeVec(tag, "Blade", blade);
        writeVec(tag, "Inward", inward);
        tag.putDouble("TargetDistance", targetDistance);
        tag.putInt("Age", age);
    }

    private static boolean hasMouth(CompoundTag tag) {
        return tag.contains("MouthX");
    }

    private static Vec3 readVec(CompoundTag tag, String key) {
        return new Vec3(tag.getDouble(key + "X"), tag.getDouble(key + "Y"), tag.getDouble(key + "Z"));
    }

    private static void writeVec(CompoundTag tag, String key, Vec3 value) {
        tag.putDouble(key + "X", value.x);
        tag.putDouble(key + "Y", value.y);
        tag.putDouble(key + "Z", value.z);
    }
}
