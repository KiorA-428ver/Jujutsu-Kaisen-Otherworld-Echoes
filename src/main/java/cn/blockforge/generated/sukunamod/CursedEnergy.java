package cn.blockforge.generated.sukunamod;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 宿傩能力使用的持久咒力与咒力流动状态。 */
public final class CursedEnergy {
    public static final int MAX_ENERGY = 18_000;
    public static final int RECOVERY_AMOUNT = 80;
    public static final int RECOVERY_INTERVAL_TICKS = 16; // 0.8 秒
    public static final int FLOW_COST = 5;
    public static final int FLOW_COST_INTERVAL_TICKS = 2; // 0.1 秒
    public static final String ENERGY_TAG = "sukunamod_cursed_energy";
    public static final String FLOW_TAG = "sukunamod_cursed_energy_flow";
    public static final String LAST_RECOVERY_TAG = "sukunamod_cursed_energy_last_recovery";
    private static final String LAST_FLOW_COST_TAG = "sukunamod_cursed_energy_last_flow_cost";
    private static final String LAST_SYNC_TAG = "sukunamod_cursed_energy_last_sync";

    private static final UUID ARMOR_MODIFIER = UUID.fromString("1c3a0d5e-7a16-4b7c-9d9b-0d7f6a4d1e01");
    private static final UUID TOUGHNESS_MODIFIER = UUID.fromString("1c3a0d5e-7a16-4b7c-9d9b-0d7f6a4d1e02");
    private static final UUID ATTACK_MODIFIER = UUID.fromString("1c3a0d5e-7a16-4b7c-9d9b-0d7f6a4d1e03");
    private static final UUID SPEED_MODIFIER = UUID.fromString("1c3a0d5e-7a16-4b7c-9d9b-0d7f6a4d1e04");
    private static final DustParticleOptions FLOW_PARTICLE = new DustParticleOptions(
            new Vector3f(0.08F, 0.35F, 1.0F), 0.8F);

    private CursedEnergy() { }

    public static void unlock(Player player) {
        if (player == null) return;
        player.getPersistentData().putInt(ENERGY_TAG, MAX_ENERGY);
        player.getPersistentData().putLong(LAST_RECOVERY_TAG, player.level().getGameTime());
        setFlow(player, false);
        sync(player, true);
    }

    public static void clear(Player player) {
        if (player == null) return;
        setFlow(player, false);
        player.getPersistentData().putInt(ENERGY_TAG, 0);
        player.getPersistentData().remove(LAST_RECOVERY_TAG);
        player.getPersistentData().remove(LAST_SYNC_TAG);
        sync(player, true);
    }

    /** 将咒力恢复到上限，并重新开始恢复计时。 */
    public static void refill(Player player) {
        if (player == null) return;
        long now = player.level().getGameTime();
        player.getPersistentData().putInt(ENERGY_TAG, MAX_ENERGY);
        player.getPersistentData().putLong(LAST_RECOVERY_TAG, now);
        player.getPersistentData().remove(LAST_FLOW_COST_TAG);
        sync(player, true);
    }

    public static int get(Player player) {
        if (player == null) return 0;
        return Math.max(0, Math.min(MAX_ENERGY,
                player.getPersistentData().getInt(ENERGY_TAG)));
    }

    public static boolean has(Player player, int amount) {
        return amount <= 0 || get(player) >= amount;
    }

    /** 消耗咒力；失败时不会扣成负数。 */
    public static boolean tryConsume(Player player, int amount) {
        if (player == null || amount < 0 || get(player) < amount) return false;
        if (amount == 0) return true;
        player.getPersistentData().putInt(ENERGY_TAG, get(player) - amount);
        sync(player, true);
        return true;
    }

    /** 按指定时间间隔扣除持续技能咒力；第一次调用会立即扣除。 */
    public static boolean tryConsumeTimed(Player player, String key, int amount, int intervalTicks) {
        if (player == null || intervalTicks <= 0) return false;
        String tag = "sukunamod_energy_timer_" + key;
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLong(tag);
        if (player.getPersistentData().contains(tag) && now - last < intervalTicks) return true;
        if (!tryConsume(player, amount)) return false;
        player.getPersistentData().putLong(tag, now);
        return true;
    }

    public static boolean isFlowing(Player player) {
        return player != null && player.getPersistentData().getBoolean(FLOW_TAG);
    }

    public static boolean toggleFlow(Player player) {
        if (player == null || !CombatMode.isServerActive(player) || !CombatMode.isUnlocked(player)) return false;
        if (isFlowing(player)) {
            setFlow(player, false);
            sync(player, true);
            player.displayClientMessage(Component.literal("[咒力流动] 已关闭"), true);
            return false;
        }
        if (!has(player, FLOW_COST)) {
            player.displayClientMessage(Component.literal("[咒力流动] 咒力不足"), true);
            return false;
        }
        setFlow(player, true);
        sync(player, true);
        player.displayClientMessage(Component.literal("[咒力流动]"), true);
        return true;
    }

    public static void setFlow(Player player, boolean active) {
        if (player == null) return;
        player.getPersistentData().putBoolean(FLOW_TAG, active);
        if (active) {
            player.getPersistentData().putLong(LAST_FLOW_COST_TAG,
                    player.level().getGameTime() - FLOW_COST_INTERVAL_TICKS);
            applyModifiers(player);
        }
        else removeModifiers(player);
    }

    /** 每个服务端玩家每 tick 调用，处理恢复、咒力流动和HUD同步。 */
    public static void tick(ServerPlayer player) {
        if (player == null || !CombatMode.isUnlocked(player) || !player.isAlive()) return;
        long now = player.level().getGameTime();
        int energy = get(player);

        long lastRecovery = player.getPersistentData().getLong(LAST_RECOVERY_TAG);
        if (!player.getPersistentData().contains(LAST_RECOVERY_TAG)) {
            lastRecovery = now;
            player.getPersistentData().putLong(LAST_RECOVERY_TAG, now);
        }
        if (now - lastRecovery >= RECOVERY_INTERVAL_TICKS && energy < MAX_ENERGY) {
            long periods = Math.min(8L, (now - lastRecovery) / RECOVERY_INTERVAL_TICKS);
            energy = Math.min(MAX_ENERGY, energy + (int) periods * RECOVERY_AMOUNT);
            player.getPersistentData().putInt(ENERGY_TAG, energy);
            player.getPersistentData().putLong(LAST_RECOVERY_TAG,
                    lastRecovery + periods * RECOVERY_INTERVAL_TICKS);
            sync(player, true);
        }

        if (isFlowing(player)) {
            applyModifiers(player);
            long lastFlowCost = player.getPersistentData().getLong(LAST_FLOW_COST_TAG);
            if (!player.getPersistentData().contains(LAST_FLOW_COST_TAG))
                lastFlowCost = now - FLOW_COST_INTERVAL_TICKS;
            if (now - lastFlowCost >= FLOW_COST_INTERVAL_TICKS) {
                if (!tryConsume(player, FLOW_COST)) {
                    setFlow(player, false);
                    sync(player, true);
                } else {
                    player.getPersistentData().putLong(LAST_FLOW_COST_TAG, now);
                }
            }
            if (isFlowing(player) && player.level() instanceof ServerLevel server) {
                server.sendParticles(FLOW_PARTICLE, player.getX(), player.getY() + 1.0D, player.getZ(),
                        6, 0.45D, 0.75D, 0.45D, 0.02D);
            }
        } else {
            removeModifiers(player);
        }

        if (now % 4L == 0L) sync(player, false);
    }

    private static void applyModifiers(Player player) {
        addModifier(player.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER,
                "咒力流动护甲", 24.0D, AttributeModifier.Operation.ADDITION);
        addModifier(player.getAttribute(Attributes.ARMOR_TOUGHNESS), TOUGHNESS_MODIFIER,
                "咒力流动护甲韧性", 8.0D, AttributeModifier.Operation.ADDITION);
        addModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_MODIFIER,
                "咒力流动攻击力", 8.0D, AttributeModifier.Operation.ADDITION);
        addModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER,
                "咒力流动移速", 0.5D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    private static void removeModifiers(Player player) {
        removeModifier(player.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER);
        removeModifier(player.getAttribute(Attributes.ARMOR_TOUGHNESS), TOUGHNESS_MODIFIER);
        removeModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_MODIFIER);
        removeModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER);
    }

    private static void addModifier(AttributeInstance instance, UUID id, String name, double amount,
                                    AttributeModifier.Operation operation) {
        if (instance != null && instance.getModifier(id) == null) {
            instance.addPermanentModifier(new AttributeModifier(id, name, amount, operation));
        }
    }

    private static void removeModifier(AttributeInstance instance, UUID id) {
        if (instance != null && instance.getModifier(id) != null) instance.removeModifier(id);
    }

    private static void sync(Player player, boolean changed) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLong(LAST_SYNC_TAG);
        if (!changed && now - last < 4L) return;
        player.getPersistentData().putLong(LAST_SYNC_TAG, now);
        JjkoaNetwork.syncEnergy(serverPlayer, get(player), isFlowing(player));
    }
}
