package cn.blockforge.generated.sukunamod;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** 服务端技能冷却，按技能分别记录，不会互相覆盖。 */
public final class SkillCooldowns {
    public static final int DISMANTLE = 0;
    public static final int EIGHT = 1;
    public static final int COMBO = 2;
    public static final int FLAME_ARROW = 3;
    public static final int REVERSE = 4;
    public static final int DOMAIN = 5;
    public static final int PURSUIT = 6;
    /** 技能5“重击”：服务端冷却与客户端显示各自独立记录。 */
    public static final int HEAVY = 7;
    /** 技能6“乱解”：0.15 秒一刀连斩 7 次（约 1.1 秒窗口），冷却略长于整个施放窗口。 */
    public static final int LUANJIE = 8;

    public static final int DISMANTLE_COOLDOWN_TICKS = 10; // 解：0.5 秒
    public static final int HEAVY_COOLDOWN_TICKS = 20; // 重击：1 秒
    public static final int EIGHT_COOLDOWN_TICKS = 36; // 捌：1.8 秒
    public static final int COMBO_COOLDOWN_TICKS = 40; // 连打：2 秒
    public static final int PURSUIT_COOLDOWN_TICKS = 10; // 追击：0.5 秒
    public static final int FLAME_ARROW_COOLDOWN_TICKS = 600; // 灶·开：30 秒
    public static final int LUANJIE_COOLDOWN_TICKS = 30; // 乱解：1.5 秒
    private static final int DOMAIN_LOCKOUT_TICKS = 600;
    private static final Map<UUID, Map<Integer, Long>> UNTIL = new HashMap<>();

    private SkillCooldowns() { }

    public static boolean tryUse(Player player, int skill, int cooldownTicks) {
        if (!isReady(player, skill)) return false;
        if (player == null || cooldownTicks <= 0 || JjkoaConfig.NO_COOLDOWN) return true;
        Map<Integer, Long> playerCooldowns = UNTIL.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        playerCooldowns.put(skill, player.level().getGameTime() + cooldownTicks);
        return true;
    }

    /** 只检查技能是否可用，不会启动新的冷却。 */
    public static boolean isReady(Player player, int skill) {
        if (player == null) return false;
        if (JjkoaConfig.NO_COOLDOWN) return true;
        Map<Integer, Long> playerCooldowns = UNTIL.get(player.getUUID());
        return playerCooldowns == null
                || playerCooldowns.getOrDefault(skill, 0L) <= player.level().getGameTime();
    }

    /**
     * 领域关闭时，除连打、追击与重击外的所有技能统一进入 30 秒冷却（术式熔断）。
     * 重击是纯体术，不走术式，按要求不受熔断影响。
     */
    public static void applyDomainClose(Player player) {
        if (player == null || JjkoaConfig.NO_COOLDOWN) return;
        long until = player.level().getGameTime() + DOMAIN_LOCKOUT_TICKS;
        Map<Integer, Long> playerCooldowns = UNTIL.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        playerCooldowns.put(DISMANTLE, until);
        playerCooldowns.put(EIGHT, until);
        playerCooldowns.put(FLAME_ARROW, until);
        playerCooldowns.put(REVERSE, until);
        playerCooldowns.put(DOMAIN, until);
        playerCooldowns.put(LUANJIE, until);
    }

    public static void tick(Player player) {
        if (player == null) return;
        Map<Integer, Long> playerCooldowns = UNTIL.get(player.getUUID());
        if (playerCooldowns == null) return;
        long now = player.level().getGameTime();
        Iterator<Map.Entry<Integer, Long>> iterator = playerCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) iterator.remove();
        }
        if (playerCooldowns.isEmpty()) UNTIL.remove(player.getUUID());
    }
}
