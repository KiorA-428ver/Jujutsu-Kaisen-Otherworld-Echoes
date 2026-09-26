package cn.blockforge.generated.sukunamod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 领域展开的客户端环境视觉：
 * 前摇（1.5 秒）期间雾与天空由原色缓慢渐变成暗红（原版天空/云色都从雾色推导，
 * 改 ViewportEvent.ComputeFogColor 一处即可同时染红雾、天空与地平线）；
 * 领域开启后满额暗红，并把地形雾收缩到"视距 8 区块"的等效距离（远平面 128 格，
 * 近平面按原版公式 128 - clamp(128/10, 4, 64) = 115.2 格），同时给圈内玩家
 * 12 秒轻微屏幕震颤，并挂上自写的暗化 + 色彩重影级色差后处理着色器（关闭领域、
 * 退出游戏或换世界时随 auraActive 复位一并卸载，不会残留）。
 * 全局唯一的后处理槽位由这里统一管理：施放者按住连续解时也挂一条更轻的
 * "柔和暗化 + 轻微色差"链（sukuna_stream），领域展开时优先让位给领域链。
 */
@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT)
public final class DomainVisuals {
    /** 满额时的暗红雾色（领域展开那种压顶的暗红天空取的中位值）。 */
    private static final float DARK_RED_R = 0.30F;
    private static final float DARK_RED_G = 0.018F;
    private static final float DARK_RED_B = 0.055F;
    /** 前摇渐变时长：30 tick = 1.5 秒，与领域生成延迟一致。 */
    private static final int PRECAST_RAMP_TICKS = 30;
    /** 前摇超时自愈：收到前摇包 40 tick 后仍未等到领域开启包就自行回落。 */
    private static final int PRECAST_TIMEOUT_TICKS = 40;
    /** 暗红强度每 tick 的最大变化量（关闭后约 1 秒褪去）。 */
    private static final float STRENGTH_STEP = 0.05F;
    /** 等同视距 8 区块的地形雾：远平面 128 格、近平面 115.2 格。 */
    private static final float FOG_FAR = 128.0F;
    private static final float FOG_NEAR = 115.2F;
    /** 12 秒 = 240 tick 的屏幕轻微震颤。 */
    private static final int DOMAIN_SHAKE_TICKS = 240;
    /** 领域后处理链（暗化 + 色差），program 走原版 minecraft 命名空间。 */
    private static final ResourceLocation POST_CHAIN = new ResourceLocation(GeneratedMod.MOD_ID,
            "shaders/post/sukuna_domain.json");
    /** 连续解后处理链（柔和暗化 + 轻微色差），强度比领域收敛一档。 */
    private static final ResourceLocation STREAM_POST_CHAIN = new ResourceLocation(GeneratedMod.MOD_ID,
            "shaders/post/sukuna_stream.json");
    /** 乱解后处理链（柔和暗化 + 中度色差），介于连续解与领域之间。 */
    private static final ResourceLocation CHAOS_POST_CHAIN = new ResourceLocation(GeneratedMod.MOD_ID,
            "shaders/post/sukuna_chaos.json");
    /** 后处理链加载失败后的重试上限，避免资源缺失时每 tick 抛一次异常。 */
    private static final int MAX_LOAD_ATTEMPTS = 20;

    /** 全局只有一个后处理槽位：领域 > 乱解 > 连续解 > 无。 */
    private enum PostKind { NONE, DOMAIN, CHAOS, STREAM }

    private static boolean precasting;
    private static boolean auraActive;
    private static int precastTicks;
    private static float strength;
    private static PostKind postKind = PostKind.NONE;
    private static int loadAttempts;
    /** 上一次客户端 tick 所在的维度：变化即视为换世界/换维度，视觉状态清零。 */
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastDimension;

    private DomainVisuals() { }

    /** 服务端包入口：mode 取 JjkoaNetwork.DOMAIN_VISUAL_PRECAST/ACTIVE/OFF。 */
    public static void applyDomainVisual(int mode) {
        if (mode == JjkoaNetwork.DOMAIN_VISUAL_ACTIVE) {
            precasting = false;
            strength = 1.0F;
            if (!auraActive) {
                auraActive = true;
                // 只有"刚进入领域/领域刚开启"的上升沿才重放 12 秒震颤；
                // 服务端每 0.5 秒的幂等对账包不会反复刷新颤动时长。
                SukunaClient.applyFlameBurst(DOMAIN_SHAKE_TICKS, 0);
            }
        } else if (mode == JjkoaNetwork.DOMAIN_VISUAL_PRECAST) {
            if (!auraActive) {
                precasting = true;
                precastTicks = 0;
            }
        } else {
            precasting = false;
            auraActive = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            resetAll();
            lastDimension = null;
            return;
        }
        // 兜底一：界主死亡（或领域因其他因素中断而 OFF 包恰好丢失）时，
        // 死亡界面一出现就清掉暗红滤镜/色差，重生后由服务端重新挂起。
        if (minecraft.player.isDeadOrDying()) {
            precasting = false;
            auraActive = false;
        }
        // 兜底二：领域绑定在它自己的维度上，换维度/换世界后客户端状态一律清零，
        // 新维度里若仍有领域，服务端的对账包会在 0.5 秒内重新挂上。
        if (!minecraft.level.dimension().equals(lastDimension)) {
            resetAll();
            lastDimension = minecraft.level.dimension();
        }
        if (precasting && ++precastTicks > PRECAST_TIMEOUT_TICKS) precasting = false;
        float target = auraActive ? 1.0F : precasting
                ? Math.min(1.0F, precastTicks / (float) PRECAST_RAMP_TICKS) : 0.0F;
        strength += Mth.clamp(target - strength, -STRENGTH_STEP, STRENGTH_STEP);
        if (!auraActive && !precasting && strength < 0.004F) strength = 0.0F;
        syncPostEffect(minecraft);
    }

    /**
     * 按"领域 &gt; 乱解 &gt; 连续解 &gt; 无"的优先级挂撤后处理着色器。原版后处理槽位只有一个：
     * 领域展开时其余柔和滤镜让位，施放乱解时挂中度链，只开连续解时挂轻量链。
     */
    private static void syncPostEffect(Minecraft minecraft) {
        PostKind wanted = auraActive ? PostKind.DOMAIN
                : SukunaClient.isChaosActive() ? PostKind.CHAOS
                : SukunaClient.isStreamHolding() ? PostKind.STREAM : PostKind.NONE;
        if (wanted != postKind) {
            // shutdownEffect 对空槽位会刷警告日志，先确认槽位上确实挂着东西。
            if (postKind != PostKind.NONE && minecraft.gameRenderer.currentEffect() != null) {
                minecraft.gameRenderer.shutdownEffect();
            }
            if (wanted == PostKind.NONE) {
                postKind = PostKind.NONE;
                loadAttempts = 0;
            } else if (loadAttempts < MAX_LOAD_ATTEMPTS) {
                if (tryLoadPostChain(minecraft, wanted)) {
                    postKind = wanted;
                    loadAttempts = 0;
                } else {
                    loadAttempts++;
                }
            }
            return;
        }
        if (postKind == PostKind.NONE) {
            loadAttempts = 0;
            return;
        }
        // 我们挂的那条链可能中途被原版/别的模组撤走（进传送门、南瓜头、眩晕、
        // 按 F4 切后处理都会 shutdownEffect 或顶掉它），一旦撤走，色差和暗化
        // 就再也回不来了——这正是"开着开着色差像没生效"的另一个来源。
        // 每 tick 用 currentEffect() 对一次账：链没了就补挂，别人占着位置就不抢。
        if (minecraft.gameRenderer.currentEffect() == null) {
            if (loadAttempts < MAX_LOAD_ATTEMPTS) {
                if (tryLoadPostChain(minecraft, postKind)) loadAttempts = 0;
                else loadAttempts++;
            }
        } else {
            loadAttempts = 0;
        }
    }

    /** 加载一条后处理链；资源缺失/驱动拒绝时返回 false，不影响其它视觉。 */
    private static boolean tryLoadPostChain(Minecraft minecraft, PostKind kind) {
        try {
            minecraft.gameRenderer.loadEffect(chainFor(kind));
            return true;
        } catch (Throwable ignored) {
            // 着色器资源缺失时只失去暗化/色差，暗红雾与迷雾收缩不受影响；
            // 重试有上限，不会每 tick 刷一次异常。
            return false;
        }
    }

    /** 按后处理种类取对应的链资源。 */
    private static ResourceLocation chainFor(PostKind kind) {
        if (kind == PostKind.CHAOS) return CHAOS_POST_CHAIN;
        if (kind == PostKind.STREAM) return STREAM_POST_CHAIN;
        return POST_CHAIN;
    }

    /** 换世界/断开连接时清零，避免暗红雾和后处理残留。 */
    public static void resetAll() {
        precasting = false;
        auraActive = false;
        precastTicks = 0;
        strength = 0.0F;
        if (postKind != PostKind.NONE) {
            Minecraft.getInstance().gameRenderer.shutdownEffect();
            postKind = PostKind.NONE;
        }
        loadAttempts = 0;
    }

    /** 前摇/领域期间的雾色：把环境算出来的雾色向暗红插值（天空、云、地平线同步变暗红）。 */
    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (strength <= 0.004F) return;
        if (event.getCamera().getFluidInCamera() != FogType.NONE) return;
        event.setRed(Mth.lerp(strength, event.getRed(), DARK_RED_R));
        event.setGreen(Mth.lerp(strength, event.getGreen(), DARK_RED_G));
        event.setBlue(Mth.lerp(strength, event.getBlue(), DARK_RED_B));
    }

    /** 领域开启期间把地形雾收缩到视距 8 区块的等效距离（只收缩、不放大）。 */
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!auraActive) return;
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        if (event.getType() != FogType.NONE) return;
        if (event.getFarPlaneDistance() <= FOG_FAR) return;
        event.setFarPlaneDistance(FOG_FAR);
        event.setNearPlaneDistance(FOG_NEAR);
    }
}
