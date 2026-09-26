package cn.blockforge.generated.sukunamod;

/** Server-side switches controlled by /jjkoa. */
public final class JjkoaConfig {
    public static final double MAX_DAMAGE_MULTIPLIER = 2100000000.0D;
    public static boolean NO_COOLDOWN = false;
    /** Global multiplier applied to the skill's fixed 0.75 base multiplier. */
    public static double DAMAGE_MULTIPLIER = 1.0D;
    /** 仅由 /jjkoa terrain <true|false> 改变；默认关闭，重启后安全保持关闭。 */
    public static boolean TERRAIN_DAMAGE = false;
    /**
     * 设定：界主离开领域范围（水平半径外）后立刻结束领域。
     * 仅由 /jjkoa leaveclose <true|false> 改变；默认开启。
     */
    public static boolean LEAVE_CLOSES_DOMAIN = true;

    private JjkoaConfig() { }
}
