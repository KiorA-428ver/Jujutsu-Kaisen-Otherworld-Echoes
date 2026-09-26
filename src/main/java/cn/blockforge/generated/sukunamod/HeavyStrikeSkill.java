package cn.blockforge.generated.sukunamod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 技能5“重击”：玩家面前 4x4x4 范围造成 2.4 倍率伤害；命中时在攻击范围中心
 * 播放放大两倍的打击帧动画，并让施放者屏幕中度震颤。
 */
public final class HeavyStrikeSkill {
    private static final double HITBOX_SIZE = 4.0D;
    /** 判定盒中心距视线起点的距离：4 格见方的盒子正好罩住面前 0~4 格。 */
    private static final double HITBOX_DISTANCE = 2.0D;
    private static final double DAMAGE_MULTIPLIER = 2.4D;
    /** 中度震颤：0.5 秒、振幅为普通轻微震荡的 1.75 倍。 */
    private static final int SHAKE_TICKS = 10;
    private static final int SHAKE_SCALE_PERCENT = 175;

    private HeavyStrikeSkill() { }

    public static void cast(Player player) {
        if (!(player.level() instanceof ServerLevel server)
                || !CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)
                || !player.isAlive()
                || !SkillCooldowns.tryUse(player, SkillCooldowns.HEAVY,
                SkillCooldowns.HEAVY_COOLDOWN_TICKS)) return;

        Vec3 direction = player.getLookAngle().normalize();
        if (direction.lengthSqr() < 1.0E-8D) return;
        Vec3 center = player.getEyePosition().add(direction.scale(HITBOX_DISTANCE));
        double half = HITBOX_SIZE * 0.5D;
        AABB area = new AABB(center.x - half, center.y - half, center.z - half,
                center.x + half, center.y + half, center.z + half);

        // 攻击范围中心：放大两倍的打击帧动画 + 一记原版重拳音效；同时挥臂出拳。
        StrikeEffects.swingArm(player);
        StrikeEffects.spawnStrikeFrame(server, center, true);
        StrikeEffects.playPunch(server, center, true);

        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && entity.isAlive())) {
            damageTarget(server, player, target);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            JjkoaNetwork.sendShake(serverPlayer, SHAKE_TICKS, SHAKE_SCALE_PERCENT);
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
        StrikeEffects.spawnHitImpact(server, target);
    }
}
