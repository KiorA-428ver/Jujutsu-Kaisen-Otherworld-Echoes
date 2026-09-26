package cn.blockforge.generated.sukunamod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Skill slots are intentionally data-driven here so later combat implementations can replace the feedback. */
public final class CombatSkills {
    private static final int REVERSE_TECHNIQUE_SLOT = CombatMode.SKILL_COUNT + 3;
    private static final String REVERSE_TECHNIQUE_NAME = "反转术式";
    private static final String[] NAMES = {"网格解", "世界斩", "连打", "追击", "重击", "乱解", "灶·开", "领域",
            "解", "捌", "咒力流动", REVERSE_TECHNIQUE_NAME};
    private static final int[] COOLDOWNS = new int[CombatMode.SKILL_COUNT + CombatMode.EXTRA_SKILL_COUNT];

    private CombatSkills() { }

    public static void activate(int slot) {
        if (slot < 0 || slot >= COOLDOWNS.length || COOLDOWNS[slot] > 0) return;
        COOLDOWNS[slot] = cooldownFor(slot);
        showSkillUsed(slot, NAMES[slot]);
    }

    /** V 键专用入口，避免把 V 键名称再从领域槽位或旧键位读取。 */
    public static void activateReverseTechnique() {
        if (COOLDOWNS[REVERSE_TECHNIQUE_SLOT] > 0) return;
        COOLDOWNS[REVERSE_TECHNIQUE_SLOT] = cooldownFor(REVERSE_TECHNIQUE_SLOT);
        showSkillUsed(REVERSE_TECHNIQUE_SLOT, REVERSE_TECHNIQUE_NAME);
    }

    private static void showSkillUsed(int slot, String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.displayClientMessage(
                Component.translatable("message.sukunamod.skill_used", Component.literal(name)), true);
    }

    public static void activateExtra(int extraIndex) {
        activate(CombatMode.SKILL_COUNT + extraIndex);
    }

    public static boolean isCoolingDown(int slot) {
        return slot >= 0 && slot < COOLDOWNS.length && COOLDOWNS[slot] > 0;
    }

    public static void tick() {
        for (int i = 0; i < COOLDOWNS.length; i++) if (COOLDOWNS[i] > 0) COOLDOWNS[i]--;
    }

    /** 退出世界/断开连接时清零冷却显示：服务端真实冷却不受影响，进服后照常生效。 */
    public static void resetAll() {
        java.util.Arrays.fill(COOLDOWNS, 0);
    }

    /** 服务端通知领域关闭后，除连打、追击、重击外所有技能统一进入 30 秒冷却（术式熔断）。 */
    public static void domainClosed() {
        int lockout = 600;
        for (int i = 0; i < COOLDOWNS.length; i++) {
            // 2=连打、3=追击、4=重击：体术不走术式，不受熔断影响。
            if (i != 2 && i != 3 && i != 4) COOLDOWNS[i] = Math.max(COOLDOWNS[i], lockout);
        }
    }

    private static int cooldownFor(int slot) {
        if (slot == 2) return 40; // 连打：2 秒
        if (slot == 4) return 20; // 重击：1 秒
        if (slot == 5) return 30; // 乱解：1.5 秒（覆盖约 1.1 秒七连斩窗口）
        if (slot == REVERSE_TECHNIQUE_SLOT) return 0; // 反转术式：无冷却
        if (slot == 6) return 600; // 灶·开：30 秒
        if (slot == 8 + 1) return 36; // 捌：1.8 秒
        return 10; // 解及其余短技能：0.5 秒
    }
}
