package cn.blockforge.generated.sukunamod;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** 技能4“追击”：记录最近一次命中的实体，并在十秒内传送过去进行追击。 */
public final class PursuitSkill {
    private static final String TARGET_UUID_TAG = "sukunamod_pursuit_target";
    private static final String TARGET_LEVEL_TAG = "sukunamod_pursuit_level";
    private static final String TARGET_EXPIRES_TAG = "sukunamod_pursuit_expires";
    private static final int MARK_DURATION_TICKS = 200;
    private static final double STRIKE_DISTANCE = 1.55D;
    private static final double DAMAGE_MULTIPLIER = 1.15D;

    private PursuitSkill() { }

    /** 攻击真正造成伤害后调用；标记本身不添加药水效果或粒子。 */
    public static void markHit(Player attacker, LivingEntity target) {
        if (!(attacker.level() instanceof ServerLevel level)
                || !attacker.isAlive() || !target.isAlive()
                || !CombatMode.isServerActive(attacker)
                || !CombatMode.isUnlocked(attacker)) return;

        clearStoredMark(attacker);
        attacker.getPersistentData().putString(TARGET_UUID_TAG, target.getUUID().toString());
        attacker.getPersistentData().putString(TARGET_LEVEL_TAG,
                level.dimension().location().toString());
        attacker.getPersistentData().putLong(TARGET_EXPIRES_TAG,
                level.getGameTime() + MARK_DURATION_TICKS);
    }

    /** 使用技能4：传送到标记目标旁边，命中后清除这次标记。 */
    public static void cast(Player player) {
        if (!(player.level() instanceof ServerLevel level)
                || !CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)
                || !player.isAlive() || !SkillCooldowns.isReady(player, SkillCooldowns.PURSUIT)) return;

        LivingEntity target = findMarkedTarget(player, level);
        if (target == null || !SkillCooldowns.tryUse(player, SkillCooldowns.PURSUIT,
                SkillCooldowns.PURSUIT_COOLDOWN_TICKS)) {
            if (target == null) clearStoredMark(player);
            return;
        }

        Vec3 targetPosition = target.position();
        Vec3 fromTarget = player.position().subtract(targetPosition);
        Vec3 horizontal = new Vec3(fromTarget.x, 0.0D, fromTarget.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            Vec3 look = target.getLookAngle();
            horizontal = new Vec3(-look.x, 0.0D, -look.z);
        }
        if (horizontal.lengthSqr() < 1.0E-6D) horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        horizontal = horizontal.normalize();

        Vec3 arrival = targetPosition.add(horizontal.scale(STRIKE_DISTANCE));
        player.teleportTo(arrival.x, arrival.y, arrival.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES,
                target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D));
        // 追击出手：面前拳区随机播一帧打击帧动画，音效换成原版"拳击生物"。
        // 传送到目标旁挥臂出拳（服务端半：周围玩家可见；第一人称由客户端本地挥）。
        StrikeEffects.swingArm(player);
        Vec3 fist = player.getEyePosition().add(player.getLookAngle().scale(1.0D));
        StrikeEffects.spawnStrikeFrame(level, fist, false);
        StrikeEffects.playPunch(level, fist);

        strike(level, player, target);
        clearStoredMark(player);
    }

    /** 清理过期标记，防止跨维度、死亡或卸载后残留无效目标。 */
    public static void tick(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!player.getPersistentData().contains(TARGET_UUID_TAG)
                || level.getGameTime() >= player.getPersistentData().getLong(TARGET_EXPIRES_TAG)) {
            clearStoredMark(player);
        }
    }

    private static LivingEntity findMarkedTarget(Player player, ServerLevel currentLevel) {
        var data = player.getPersistentData();
        if (!data.contains(TARGET_UUID_TAG)
                || currentLevel.getGameTime() >= data.getLong(TARGET_EXPIRES_TAG)) return null;
        UUID targetId;
        try {
            targetId = UUID.fromString(data.getString(TARGET_UUID_TAG));
        } catch (IllegalArgumentException exception) {
            return null;
        }

        String levelName = data.getString(TARGET_LEVEL_TAG);
        if (!currentLevel.dimension().location().toString().equals(levelName)) return null;
        Entity entity = currentLevel.getEntity(targetId);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private static void strike(ServerLevel level, Player player, LivingEntity target) {
        ItemStack held = player.getMainHandItem();
        double heldDamage = Math.max(2.0D, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float damage = (float) (heldDamage * DAMAGE_MULTIPLIER * JjkoaConfig.DAMAGE_MULTIPLIER);
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        if (!target.hurt(level.damageSources().playerAttack(player), damage)) return;
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        int fire = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, held);
        if (fire > 0) target.setSecondsOnFire(fire * 4);

        // 命中反馈：爆炸粒子与烟花音效已移除，改为随机贴在目标身上的打击命中帧动画。
        StrikeEffects.spawnHitImpact(level, target);
    }

    private static void clearStoredMark(Player player) {
        player.getPersistentData().remove(TARGET_UUID_TAG);
        player.getPersistentData().remove(TARGET_LEVEL_TAG);
        player.getPersistentData().remove(TARGET_EXPIRES_TAG);
    }
}
