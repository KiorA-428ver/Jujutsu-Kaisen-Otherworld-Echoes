package cn.blockforge.generated.sukunamod;

import net.minecraft.world.entity.player.Player;

/** Shared combat-mode state. The authoritative gameplay hooks remain client-side for this UI framework. */
public final class CombatMode {
    private CombatMode() { }

    public static final int SKILL_COUNT = 8;
    public static final int EXTRA_SKILL_COUNT = 4;
    public static final String UNLOCKED_TAG = "sukunamod_unlocked";
    public static final String ACTIVE_TAG = "sukunamod_combat_active";
    private static boolean active;

    public static boolean isActive() {
        return active;
    }

    public static boolean toggle(Player player) {
        if (!isUnlocked(player)) {
            active = false;
            return false;
        }
        active = !active;
        return active;
    }

    public static void forceOff() {
        active = false;
    }

    /** 接收服务端在换维度/重生后同步的客户端战斗模式。 */
    public static void setClientActive(boolean value) {
        active = value;
    }

    public static boolean isUnlocked(Player player) {
        return player != null && player.getPersistentData().getBoolean(UNLOCKED_TAG);
    }

    public static void unlock(Player player) {
        if (player == null) return;
        boolean wasUnlocked = isUnlocked(player);
        player.getPersistentData().putBoolean(UNLOCKED_TAG, true);
        if (!player.getPersistentData().contains(CursedEnergy.ENERGY_TAG)) {
            CursedEnergy.unlock(player);
        }
    }

    public static void clearUnlock(Player player) {
        if (player == null) return;
        player.getPersistentData().putBoolean(UNLOCKED_TAG, false);
        player.getPersistentData().putBoolean(ACTIVE_TAG, false);
        CursedEnergy.clear(player);
    }

    public static boolean isServerActive(Player player) {
        return player != null && player.getPersistentData().getBoolean(ACTIVE_TAG);
    }

    public static void setServerActive(Player player, boolean value) {
        if (player != null) player.getPersistentData().putBoolean(ACTIVE_TAG, value && isUnlocked(player));
    }
}
