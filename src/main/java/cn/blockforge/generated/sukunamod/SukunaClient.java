package cn.blockforge.generated.sukunamod;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.joml.Matrix4f;

import java.nio.IntBuffer;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SukunaClient {
    private static final int[] NUMBERS = {GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3,
            GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7,
            GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_0};
    private static final boolean[] WAS_DOWN = new boolean[NUMBERS.length];
    private static final int DOMAIN_CHARGE_TICKS = 30; // 按下后延迟 1.5 秒生成领域
    private static final int COMBO_MAX_HOLD_TICKS = 60; // 连打长按上限：3 秒
    private static KeyMapping combatToggle;
    private static KeyMapping reverseKey;
    private static KeyMapping[] extraKeys;
    private static InputConstants.Key[] savedHotbarKeys;
    private static boolean domainActive;
    private static int cursedEnergy;
    private static boolean energyFlowing;
    private static boolean hotbarMappingsSuppressed;
    /** 灶·开爆炸的屏幕颤动剩余 tick。 */
    private static int shakeTicks;
    /** 当前这轮震屏的振幅倍率：1.0=轻微档（解/灶·开），重击等技能用 ShakePacket 临时抬高。 */
    private static float shakeScale = 1.0F;
    /** 乱解方向性震屏：为真时本轮震动沿 shakeDirX/Y 给定的方向摆动，而非原版李萨如轨迹。 */
    private static boolean directionalShake;
    /** 乱解本轮震动的单位方向（服务端每刀给一个不同的角度）。 */
    private static float shakeDirX = 1.0F;
    private static float shakeDirY = 1.0F;
    /** 乱解"柔和暗化 + 中度色差"后处理的剩余 tick（服务端每刀续期，DomainVisuals 轮询）。 */
    private static int chaosTicks;
    /** 冲击帧：包线程写入的开播请求（灶·开第五发闪光 / 世界斩脱手），渲染线程取走后复位。 */
    private static volatile ImpactRequest impactRequest;
    /** 冲击帧阶段：0 空闲，1 播放 A 段（黑白反色），2 播放 B 段（正常黑白）。 */
    private static int impactPhase;
    private static long impactDeadlineMs;
    private static int impactFrameIndex;
    /** 冲击帧起播时刻（毫秒），用于让画面震颤幅度随时间衰减、也用于 A 段计时。 */
    private static long impactStartMs;
    /** 本轮冲击帧的播放参数（只在渲染线程访问，起播时从 impactRequest 取走）。 */
    private static int impactPhaseAFrames;
    private static long impactPhaseAMs;
    private static long impactPhaseBMs;
    private static int impactShakeTicks;
    private static int impactShakePercent;
    /** 震颤最大振幅（GUI 像素）。 */
    private static final int IMPACT_JITTER_PX = 18;
    /** 冲击帧震颤取点用（只在渲染线程访问）。 */
    private static final java.util.Random IMPACT_RANDOM = new java.util.Random();
    private static int impactTexCounter;
    private static ResourceLocation impactLocBw;
    private static ResourceLocation impactLocInverted;
    private SukunaClient() { }

    /** 服务端包：叠加屏幕颤动 tick 数；冲击帧帧数大于 0（第五发闪光放到最大）时立即截屏开播冲击帧。 */
    public static void applyFlameBurst(int shake, int frames) {
        shakeTicks = Math.max(shakeTicks, shake);
        shakeScale = 1.0F;
        if (frames > 0) {
            // 灶·开维持原节奏：A 段两帧极致黑白反色，B 段 0.25 秒正常黑白，不额外震屏。
            impactRequest = new ImpactRequest(2, 0L, 250L, 0, 0);
        }
    }

    /**
     * 世界斩脱手包：开播一次"先 0.15 秒黑白反色、再 0.2 秒正常黑白"的冲击帧，
     * 每段出现时各叠加一次中度震屏（shakePercent%，持续 shakeTicks tick）。
     */
    public static void applySlashImpact(int shake, int percent, int invertedMs, int normalMs) {
        impactRequest = new ImpactRequest(0, Math.max(1, invertedMs), Math.max(1, normalMs),
                Math.max(0, shake), Math.max(0, percent));
    }

    /** 一轮冲击帧的开播参数（包线程写、渲染线程读，靠 volatile 的 impactRequest 交接）。 */
    private static final class ImpactRequest {
        /** 大于 0：A 段按渲染帧数播放（灶·开的"两帧反色"）；等于 0：A 段按 phaseAMs 计时。 */
        private final int phaseAFrames;
        private final long phaseAMs;
        private final long phaseBMs;
        private final int shakeTicks;
        private final int shakePercent;

        private ImpactRequest(int phaseAFrames, long phaseAMs, long phaseBMs,
                              int shakeTicks, int shakePercent) {
            this.phaseAFrames = phaseAFrames;
            this.phaseAMs = phaseAMs;
            this.phaseBMs = phaseBMs;
            this.shakeTicks = shakeTicks;
            this.shakePercent = shakePercent;
        }
    }

    /** 重击等服务端回推的一次性震屏：持续 ticks 刻、振幅为轻微档的 percent%（中度=175）。 */
    public static void applyShake(int ticks, int percent) {
        shakeTicks = Math.max(shakeTicks, ticks);
        shakeScale = Math.max(shakeScale, percent / 100.0F);
    }

    /**
     * 乱解每一刀的服务端包：以 percent% 振幅沿 directionDegrees 方向震一下（持续 ticks 刻），
     * 并把"柔和暗化 + 中度色差"后处理续期到 visualTicks 刻。四刀四个方向，
     * 方向由服务端按象限错开生成，保证"每次震动的方向均不一样"。
     */
    public static void applyChaosStrike(int ticks, int percent, float directionDegrees,
                                        int visualTicks) {
        shakeTicks = Math.max(shakeTicks, ticks);
        shakeScale = Math.max(shakeScale, percent / 100.0F);
        double radians = Math.toRadians(directionDegrees);
        shakeDirX = (float) Math.cos(radians);
        shakeDirY = (float) Math.sin(radians);
        directionalShake = true;
        chaosTicks = Math.max(chaosTicks, visualTicks);
    }

    /** 乱解后处理是否仍在挂：DomainVisuals 每客户端 tick 轮询，按优先级占后处理槽位。 */
    public static boolean isChaosActive() {
        return chaosTicks > 0;
    }

    /** 普通解/后撤解释放瞬间：一次性轻微震荡屏幕 0.4 秒（只作用于施放者自己的画面）。 */
    static void pulseCastShake() {
        shakeTicks = Math.max(shakeTicks, ClientEvents.CAST_PULSE_TICKS);
        shakeScale = 1.0F;
    }

    /** 施放者当前是否正处于连续解施放中：驱动"柔和暗化 + 轻微色差"后处理的挂撤。 */
    public static boolean isStreamHolding() {
        return ClientEvents.streamHold;
    }

    public static void setDomainActive(boolean active) {
        domainActive = active;
        // 领域生成后显示满格；下一次按下数字 8 才会关闭领域。
        ClientEvents.domainHoldTicks = active ? DOMAIN_CHARGE_TICKS : 0;
    }

    public static void applyUnlockSync(boolean unlocked, boolean active) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.getPersistentData().putBoolean(CombatMode.UNLOCKED_TAG, unlocked);
            minecraft.player.getPersistentData().putBoolean(CombatMode.ACTIVE_TAG, unlocked && active);
            CombatMode.setClientActive(unlocked && active);
            if (!unlocked) {
                CombatMode.forceOff();
                cursedEnergy = 0;
                energyFlowing = false;
            }
        }
    }

    public static void applyEnergySync(int energy, boolean flowing) {
        cursedEnergy = Math.max(0, Math.min(CursedEnergy.MAX_ENERGY, energy));
        energyFlowing = flowing;
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        combatToggle = new KeyMapping("key.sukunamod.combat_toggle", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_T, "key.categories.sukunamod");
        event.register(combatToggle);
        // 左侧第四个可改技能键使用 V，名称固定为反转术式；数字 4 则单独显示追击。
        reverseKey = key("reverse_technique", GLFW.GLFW_KEY_V);
        extraKeys = new KeyMapping[]{
                key("extra_1", GLFW.GLFW_KEY_Z), key("extra_2", GLFW.GLFW_KEY_X),
                key("extra_3", GLFW.GLFW_KEY_C), reverseKey
        };
        for (KeyMapping key : extraKeys) event.register(key);
    }

    private static KeyMapping key(String id, int code) {
        return new KeyMapping("key.sukunamod." + id, InputConstants.Type.KEYSYM,
                code, "key.categories.sukunamod");
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "sukuna_skills", SukunaClient::renderOverlay);
        // 灶·开爆炸的冲击帧：盖在所有界面元素之上，按渲染帧播放。
        event.registerAboveAll("sukuna_impact_frame", SukunaClient::renderImpactFrame);
    }

    /**
     * 黑白冲击帧：收到开播请求的瞬间截取玩家当前屏幕，先播黑白反色帧、再接正常黑白帧。
     * 灶·开第五发闪光＝"2 渲染帧反色 + 0.25 秒正常黑白"；世界斩脱手＝"0.15 秒反色 +
     * 0.2 秒正常黑白"，且每出现一帧就震一下屏幕（一轮共两下、中度振幅）。
     * 整段播放期间画面本身还带逐渲染帧随机偏移的震颤。
     */
    private static void renderImpactFrame(net.minecraftforge.client.gui.overlay.ForgeGui gui,
                                          GuiGraphics graphics, float partialTick,
                                          int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            impactRequest = null;
            if (impactPhase != 0) {
                impactPhase = 0;
                endImpactTextures();
            }
            return;
        }
        ImpactRequest request = impactRequest;
        if (request != null) {
            impactRequest = null;
            beginImpactSequence(request);
        }
        if (impactPhase == 0) return;
        long now = Util.getMillis();
        if (now >= impactDeadlineMs) {
            impactPhase = 0;
            endImpactTextures();
            return;
        }
        if (impactPhase == 1) {
            // A 段：黑白反色帧。灶·开按渲染帧数计（2 帧），世界斩按 0.15 秒时长计。
            drawImpactFrame(graphics, impactLocInverted, now, width, height);
            boolean phaseAOver = impactPhaseAFrames > 0
                    ? (++impactFrameIndex >= impactPhaseAFrames)
                    : (now - impactStartMs >= impactPhaseAMs);
            if (phaseAOver) {
                impactPhase = 2;
                impactDeadlineMs = now + impactPhaseBMs;
                pulseImpactShake();
            }
        } else {
            // B 段：正常黑白（不反色）。
            drawImpactFrame(graphics, impactLocBw, now, width, height);
        }
    }

    /** 冲击帧每出现一帧叠加一次震屏（世界斩＝两帧两下中度）；没配置震屏参数的播放路径不做任何事。 */
    private static void pulseImpactShake() {
        if (impactShakeTicks > 0 && impactShakePercent > 0) {
            applyShake(impactShakeTicks, impactShakePercent);
        }
    }

    /** 整幅绘制一帧冲击画面并震颤：偏移逐帧随机、振幅随播放进度衰减，四周留出边距避免露出黑边。 */
    private static void drawImpactFrame(GuiGraphics graphics, ResourceLocation texture, long now,
                                        int width, int height) {
        int pad = IMPACT_JITTER_PX;
        double decay = Math.max(0.0D, 1.0D - (now - impactStartMs) / 400.0D);
        java.util.Random random = IMPACT_RANDOM;
        int jx = (int) Math.round((random.nextDouble() * 2.0D - 1.0D) * pad * decay);
        int jy = (int) Math.round((random.nextDouble() * 2.0D - 1.0D) * pad * decay);
        graphics.blit(texture, jx - pad, jy - pad, 0.0F, 0.0F,
                width + pad * 2, height + pad * 2, width + pad * 2, height + pad * 2);
    }

    /** 收到包即截屏，制成 极致黑白/黑白反色 两张纹理并按请求里的节奏开播。 */
    private static void beginImpactSequence(ImpactRequest request) {
        endImpactTextures();
        impactPhaseAFrames = request.phaseAFrames;
        impactPhaseAMs = request.phaseAMs;
        impactPhaseBMs = request.phaseBMs;
        impactShakeTicks = request.shakeTicks;
        impactShakePercent = request.shakePercent;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            NativeImage capture = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
            NativeImage bw = capture.mappedCopy(SukunaClient::toExtremeBlackWhite);
            NativeImage inverted = bw.mappedCopy(SukunaClient::invertRgb);
            try {
                impactLocBw = registerImpactTexture(bw);
                impactLocInverted = registerImpactTexture(inverted);
            } finally {
                capture.close();
                bw.close();
                inverted.close();
            }
            impactPhase = 1;
            impactFrameIndex = 0;
            impactStartMs = Util.getMillis();
            impactDeadlineMs = impactPhaseAFrames > 0
                    ? impactStartMs + 1000L                     // 按帧播：兜底上限，进 B 段后改判
                    : impactStartMs + impactPhaseAMs + impactPhaseBMs; // 按时长播：A + B 总时长
            pulseImpactShake();                                // 第一帧冲击帧出现：先震一下
        } catch (Throwable ignored) {
            impactPhase = 0;
            endImpactTextures();
        }
    }

    /** 上传一张整屏纹理并注册到纹理管理器，交给 GuiGraphics 正常渲染。 */
    private static ResourceLocation registerImpactTexture(NativeImage image) {
        int glId = TextureUtil.generateTextureId();
        GlStateManager._bindTexture(glId);
        // 内部格式 32856=GL_SRGB8_ALPHA8，与主渲染目标颜色附件一致，回读像素可原样显示。
        GlStateManager._texImage2D(3553, 0, 32856, image.getWidth(), image.getHeight(),
                0, 6408, 5121, (IntBuffer) null);
        image.upload(0, 0, 0, false);
        AbstractTexture texture = new AbstractTexture() {
            {
                this.id = glId;
            }

            @Override
            public void load(ResourceManager manager) {
                // 内容手动上传，资源热重载不覆盖。
            }
        };
        ResourceLocation location = new ResourceLocation(GeneratedMod.MOD_ID,
                "dynamic/sukuna_impact_" + (++impactTexCounter));
        Minecraft.getInstance().getTextureManager().register(location, texture);
        return location;
    }

    /** 释放上一轮冲击帧的两张纹理。 */
    private static void endImpactTextures() {
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        if (impactLocBw != null) {
            manager.release(impactLocBw);
            impactLocBw = null;
        }
        if (impactLocInverted != null) {
            manager.release(impactLocInverted);
            impactLocInverted = null;
        }
    }

    /** 极致黑白：按感知亮度硬阈值，只留纯黑纯白（像素为内存序 0xAABBGGRR）。 */
    private static int toExtremeBlackWhite(int pixel) {
        int red = pixel & 255;
        int green = (pixel >> 8) & 255;
        int blue = (pixel >> 16) & 255;
        int luminance = (299 * red + 587 * green + 114 * blue) / 1000;
        int shade = luminance >= 128 ? 255 : 0;
        return (pixel & 0xFF000000) | (shade << 16) | (shade << 8) | shade;
    }

    /** RGB 逐通道反色；作用在黑白图上即黑白互换。 */
    private static int invertRgb(int pixel) {
        return (pixel & 0xFF000000)
                | ((255 - ((pixel >> 16) & 255)) << 16)
                | ((255 - ((pixel >> 8) & 255)) << 8)
                | (255 - (pixel & 255));
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GeneratedMod.DISMANTLE_PROJECTILE.get(), DismantleRenderer::new);
        event.registerEntityRenderer(GeneratedMod.SHRINE_ENTITY.get(), ShrineRenderer::new);
        event.registerEntityRenderer(GeneratedMod.FLAME_ARROW_ENTITY.get(), FlameArrowRenderer::new);
        // 隐形破坏斩击：注册恒不绘制的渲染器，游戏里看不到它任何东西。
        event.registerEntityRenderer(GeneratedMod.TRENCH_SLASH.get(), TrenchSlashRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(GeneratedMod.KITCHEN_FLAME.get(), KitchenFlameParticle.Provider::new);
        event.registerSpriteSet(GeneratedMod.REVERSE_FLAME.get(), KitchenFlameParticle.Provider::new);
        event.registerSpriteSet(GeneratedMod.KITCHEN_FLAME_2X2.get(), KitchenFlameBigParticle.Provider::new);
        event.registerSpriteSet(GeneratedMod.KITCHEN_FLAME_PILLAR.get(), KitchenFlamePillarParticle.Provider::new);
        event.registerSpriteSet(GeneratedMod.FLASH_CROSS_1.get(), FlashCrossParticle.Provider1::new);
        event.registerSpriteSet(GeneratedMod.FLASH_CROSS_2.get(), FlashCrossParticle.Provider2::new);
        event.registerSpriteSet(GeneratedMod.FLASH_CROSS_5.get(), FlashGrowParticle.Provider::new);
        event.registerSpriteSet(GeneratedMod.STRIKE_FRAME.get(), StrikeFrameParticle.Provider::new);
        event.registerSpriteSet(GeneratedMod.STRIKE_FRAME_BIG.get(), StrikeFrameParticle.BigProvider::new);
        event.registerSpriteSet(GeneratedMod.STRIKE_HIT_FRAME.get(), StrikeFrameParticle.HitProvider::new);
    }

    @Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private static int holdTicks;
        private static int gridHoldTicks;
        private static int gridStreamTimer;
        private static int domainHoldTicks;
        private static boolean reverseKeyWasDown;
        private static int comboHoldTicks;
        private static boolean comboMaxed;
        private static boolean worldSlashWasDown;
        private static int pendingDismantlePresses;
        private static boolean eightComboUsed;
        /** 当前是否正在施放连续解（每客户端 tick 刷新，驱动持续震屏与后处理）。 */
        private static boolean streamHold;
        /**
         * 下坠缓冲的剩余刻数：只有"这一 tick 真的发出了连续斩包"才会续期。
         * 上一版这里是用一套按键+咒力的判定式去猜"玩家应该正在连发"，猜错就完全
         * 不减速（用户反馈下坠缓冲没生效）；现在直接以实际发包为准，不再依赖任何
         * 客户端推断，施放中断后 5 刻内自然解除。
         */
        private static int streamGlideTicks;
        /** 连续网格斩每 3 刻发一包，续期窗口取 5 刻刚好覆盖发包间隔。 */
        private static final int STREAM_GLIDE_REFRESH_TICKS = 5;
        /**
         * 连续解 / 连续网格斩期间的坠落减速：原版玩家下坠终速 3.92 格/tick
         * （重力 0.08、垂直阻力 0.98 不动点）。初版减缓 80%，封顶到 0.784；
         * 本轮在此基础上再慢 85%，即 0.784 × 0.15 = 0.1176 格/tick，
         * 合计只剩正常终速的 3%（约 2.35 格/秒）。
         * 原版在 travel() 里是"先按当前速度位移、再叠加重力与阻力"，所以在物理之前
         * 把下坠速度封顶到该值，本 tick 的实际位移就是封顶值。
         */
        private static final double STREAM_GLIDE_FALL_PER_TICK = 0.1176D;
        /** 连续解期间的持续震屏振幅比例（普通解/后撤解的一次性震荡用 1.0）。 */
        private static final float STREAM_SHAKE_SCALE = 0.55F;
        /** 普通解/后撤解施放时的一次性轻微震荡时长：8 tick = 0.4 秒。 */
        private static final int CAST_PULSE_TICKS = 8;
        private ClientEvents() { }

        /**
         * 本地第一人称挥臂：服务端的挥臂动画包刻意不发给施放者自己，
         * 所以"自己的手臂在挥"这半必须在客户端触发；LocalPlayer.swing
         * 会同时把 ServerboundSwingPacket 送回服务器让别的玩家也看到。
         */
        private static void swingOwnArm(Minecraft minecraft) {
            if (minecraft.player != null) minecraft.player.swing(InteractionHand.MAIN_HAND);
        }

        /** 连续解期间保持震屏计数不为 0（每 tick 重新续上，松键即自然衰减）。 */
        private static void keepStreamShakeAlive() {
            if (streamHold) shakeTicks = Math.max(shakeTicks, 2);
        }

        /**
         * 当前是否正在施放连续解：与 clientTick 里发送 STREAM_DISMANTLE 包的
         * 触发条件保持一致——战斗模式开启且已解锁、按住解键 + 潜行、未按后退
         * （后撤解另算）、未超 100 次施放上限、咒力够下一次施放。
         */
        private static boolean slashStreaming(Minecraft minecraft) {
            return minecraft.player != null && minecraft.screen == null && extraKeys != null
                    && CombatMode.isActive() && CombatMode.isUnlocked(minecraft.player)
                    && extraKeys[0].isDown() && minecraft.options.keyShift.isDown()
                    && !minecraft.options.keyDown.isDown()
                    && holdTicks < 100 && cursedEnergy >= 10;
        }

        /**
         * 连续解的"宿傩斩击音效循环"已按要求移除：施放期间不再播放任何循环音频，
         * 这里改为刷新持续状态 streamHold——松键、开界面、关闭战斗模式或咒力耗尽
         * 时立即置假，驱动持续轻微震屏与"柔和暗化 + 轻微色差"后处理的起停。
         * 同时让下坠缓冲的剩余刻数在这里倒数：真正的续期发生在发出连续斩包的那一刻
         * （见 {@link #markStreamGlide()}），所以这里只负责"停止施放后自己 decay 掉"。
         */
        private static void updateSlashStreamState(Minecraft minecraft) {
            streamHold = slashStreaming(minecraft);
            if (streamGlideTicks > 0) streamGlideTicks--;
            keepStreamShakeAlive();
        }

        /** 发出一次连续斩包（连续解 / 连续网格斩）：把下坠缓冲续期一小段窗口。 */
        private static void markStreamGlide() {
            streamGlideTicks = STREAM_GLIDE_REFRESH_TICKS;
        }

        /**
         * 连续解 / 连续网格斩期间的坠落减速：本地玩家的物理是权威的，
         * 位置会照常上报给服务端并同步给其他玩家，所以在这里把下坠速度封顶即可，
         * 不需要额外发包。只作用于自己的玩家实体、只压下降段，飞行/骑乘不干预；
         * 落地时下坠速度本来就小于封顶值，判定自然不成立，不需要额外排除。
         * 每刻在三个位置各调一次（客户端 tick 起始、玩家 tick 起始与结束），
         * 保证无论原版物理在哪一步执行，本 tick 的位移都已经被压住。
         */
        public static void applyStreamGlide(Player player) {
            if (streamGlideTicks <= 0) return;
            if (player == null || player != Minecraft.getInstance().player) return;
            if (player.isPassenger() || player.getAbilities().flying) return;
            Vec3 motion = player.getDeltaMovement();
            if (motion.y < -STREAM_GLIDE_FALL_PER_TICK) {
                player.setDeltaMovement(motion.x, -STREAM_GLIDE_FALL_PER_TICK, motion.z);
            }
        }

        /**
         * 连续解/连续网格斩期间：本地玩家的下坠速度先减缓 80%、再减缓 85%
         * （合计封顶到正常终速的 3%）。
         * START 与 END 两个相位都封顶：START 压住本 tick 的位移，END 保证下一 tick
         * 的起始速度不会超过封顶值，不依赖原版重力/阻力的先后顺序。
         * 事件挂在客户端专用订阅类里，专用服务端完全不会加载这段逻辑。
         */
        @SubscribeEvent
        public static void streamGlideTick(TickEvent.PlayerTickEvent event) {
            applyStreamGlide(event.player);
        }

        @SubscribeEvent
        public static void keyInput(InputEvent.Key event) {
            if (event.getAction() != GLFW.GLFW_PRESS || extraKeys == null) return;
            if (extraKeys[0].matches(event.getKey(), event.getScanCode())) pendingDismantlePresses++;
        }

        @SubscribeEvent
        public static void mouseInput(InputEvent.MouseButton event) {
            if (event.getAction() != GLFW.GLFW_PRESS || extraKeys == null) return;
            if (extraKeys[0].matchesMouse(event.getButton())) pendingDismantlePresses++;
        }

        /**
         * 退出到标题/断开服务器时的客户端复位。退回标题并不重载模组类，静态的
         * domainActive 会跨存档存活：而领域服务端状态在退档时被清、给下线玩家发的
         * "领域已关闭"包也送不到，再进存档后客户端仍以为领域开着——按 8 只会发
         * "关闭领域"包，服务端已经没有领域可关，于是永远无法再次展开。这里把
         * 领域标记、前摇计数和技能冷却显示一并归零（服务端登录后也会权威同步一次）。
         */
        @SubscribeEvent
        public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            domainActive = false;
            domainHoldTicks = 0;
            shakeTicks = 0;
            directionalShake = false;
            chaosTicks = 0;
            streamHold = false;
            streamGlideTicks = 0;
            CombatSkills.resetAll();
            DomainVisuals.resetAll();
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            // 屏幕颤动按游戏 tick 倒数（放在最前，避免被后面的提前 return 跳过）。
            if (event.phase == TickEvent.Phase.END) {
                if (chaosTicks > 0) chaosTicks--;
                if (shakeTicks > 0 && --shakeTicks == 0) {
                    shakeScale = 1.0F;
                    directionalShake = false;
                }
            }
            if (event.phase != TickEvent.Phase.START || combatToggle == null) return;
            Minecraft minecraft = Minecraft.getInstance();
            syncHotbarMappings(minecraft);
            // 连续解持续状态（震屏 + 后处理）的刷新先于一切提前 return：
            // 玩家为空/开着界面也要能把持续效果关掉。
            updateSlashStreamState(minecraft);
            // 下坠缓冲在客户端 tick 起始也封一次顶：这一刻晚于上一刻的玩家物理、
            // 又早于本刻 ClientLevel 里的玩家 tick，所以不依赖 PlayerTickEvent 是否
            // 按预期到达，物理上一定能在本刻的位移之前生效。
            applyStreamGlide(minecraft.player);
            if (minecraft.player == null || minecraft.screen != null) return;
            CombatSkills.tick();
            if (!CombatMode.isUnlocked(minecraft.player)) {
                CombatMode.forceOff();
                System.arraycopy(readNumberKeys(minecraft), 0, WAS_DOWN, 0, WAS_DOWN.length);
                holdTicks = 0;
                gridHoldTicks = 0;
                gridStreamTimer = 0;
                streamHold = false;
                streamGlideTicks = 0;
                domainHoldTicks = 0;
                reverseKeyWasDown = false;
                comboHoldTicks = 0;
                comboMaxed = false;
                worldSlashWasDown = false;
                pendingDismantlePresses = 0;
                eightComboUsed = false;
                if (WAS_DOWN[6]) {
                    JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                            JjkoaNetwork.ActionPacket.FLAME_ARROW_RELEASE, false));
                }
                return;
            }
            if (combatToggle.consumeClick()) {
                boolean enabled = CombatMode.toggle(minecraft.player);
                syncHotbarMappings(minecraft);
                JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(JjkoaNetwork.ActionPacket.TOGGLE_MODE, enabled));
                minecraft.player.displayClientMessage(Component.translatable(
                        enabled ? "message.sukunamod.combat_on" : "message.sukunamod.combat_off"), true);
            }
            boolean[] down = readNumberKeys(minecraft);
            // 战斗模式下只拦截原版快捷栏数字键的点击事件，保留滚轮切换物品栏。
            // 技能仍使用下面的原始按键状态处理，因此 1~8 不会丢失技能输入。
            if (!CombatMode.isActive()) {
                if (worldSlashWasDown) {
                    JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                            JjkoaNetwork.ActionPacket.WORLD_SLASH_RELEASE, false));
                }
                if (WAS_DOWN[6]) {
                    JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                            JjkoaNetwork.ActionPacket.FLAME_ARROW_RELEASE, false));
                }
                System.arraycopy(down, 0, WAS_DOWN, 0, down.length);
                holdTicks = 0;
                gridHoldTicks = 0;
                gridStreamTimer = 0;
                streamHold = false;
                streamGlideTicks = 0;
                domainHoldTicks = 0;
                reverseKeyWasDown = false;
                comboHoldTicks = 0;
                comboMaxed = false;
                worldSlashWasDown = false;
                pendingDismantlePresses = 0;
                eightComboUsed = false;
                return;
            }
            for (int i = 0; i < 7; i++) {
                if (down[i] && !WAS_DOWN[i]) {
                    if (i == 0) sendDismantle(JjkoaNetwork.ActionPacket.GRID_DISMANTLE, true);
                    else if (i == 3) {
                        // 追击是体术：按下瞬间本地挥一次臂（自己第一人称看得到）。
                        swingOwnArm(minecraft);
                        JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                                JjkoaNetwork.ActionPacket.PURSUIT, CombatMode.isActive()));
                        CombatSkills.activate(i);
                    } else if (i == 4) {
                        // 技能5“重击”：面前 4x4x4、2.4 倍率伤害，由服务端结算并回推中度震屏。
                        swingOwnArm(minecraft);
                        JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                                JjkoaNetwork.ActionPacket.HEAVY_STRIKE, CombatMode.isActive()));
                        CombatSkills.activate(i);
                    } else if (i == 5) {
                        // 技能6“乱解”：以自身为中心 9x9x9 内约 1.1 秒七连斩，
                        // 每刀由服务端逐刀结算伤害/破坏并回推一次方向性震屏与后处理续期。
                        JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                                JjkoaNetwork.ActionPacket.LUANJIE, CombatMode.isActive()));
                        CombatSkills.activate(i);
                    } else if (i == 2) CombatSkills.activate(i);
                    else if (i == 6) CombatSkills.activate(i);
                    else CombatSkills.activate(i);
                }
            }
            // V 键是按住型反转术式；松键后服务端通过输入超时停止治疗和粒子。
            boolean reverseDown = reverseKey.isDown();
            if (reverseDown) {
                JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                        JjkoaNetwork.ActionPacket.REVERSE_HOLD, CombatMode.isActive()));
                if (!reverseKeyWasDown) CombatSkills.activateReverseTechnique();
            }
            reverseKeyWasDown = reverseDown;

            // 数字 8 是领域展开键：按下一次立即开始前摇，1.5 秒后生成领域；释放按键不取消。
            boolean domainDown = down[7];
            boolean domainWasDown = WAS_DOWN[7];
            if (domainDown && !domainWasDown) {
                if (domainActive) {
                    sendDomainRelease();
                } else {
                    domainHoldTicks = 1;
                    JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                            JjkoaNetwork.ActionPacket.DOMAIN_HOLD, CombatMode.isActive()));
                }
            } else if (!domainActive && domainHoldTicks > 0
                    && domainHoldTicks < DOMAIN_CHARGE_TICKS) {
                domainHoldTicks++;
            }
            if (!domainDown && domainWasDown && !domainActive
                    && domainHoldTicks >= DOMAIN_CHARGE_TICKS) {
                domainHoldTicks = 0;
            }
            // 数字 2 是世界斩：按住蓄力 1.5 秒，松开后由服务端判断是否发射。
            if (down[1]) {
                JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                        JjkoaNetwork.ActionPacket.WORLD_SLASH_HOLD, CombatMode.isActive()));
            } else if (worldSlashWasDown) {
                JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                        JjkoaNetwork.ActionPacket.WORLD_SLASH_RELEASE, false));
            }
            worldSlashWasDown = down[1];

            // 数字 3 是按住型连打，最多持续 3 秒；松开后才能再次开始。
            if (down[2] && !comboMaxed) {
                if (comboHoldTicks < COMBO_MAX_HOLD_TICKS) {
                    // 连打期间每刻请求挥臂：原版 swing 自带"半程内不重挥"的节奏保护，
                    // 于是按住就是最快频率的连续出拳动画（服务端那半同时广播给别的玩家）。
                    swingOwnArm(minecraft);
                    JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                            JjkoaNetwork.ActionPacket.COMBO_HOLD, CombatMode.isActive()));
                    comboHoldTicks++;
                } else {
                    comboMaxed = true;
                }
            } else if (!down[2]) {
                comboHoldTicks = 0;
                comboMaxed = false;
            }
            // 数字 7 是按住蓄力型灶·开，松开时只发送一次发射包。
            if (down[6]) {
                JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                        JjkoaNetwork.ActionPacket.FLAME_ARROW_HOLD, CombatMode.isActive()));
            } else if (WAS_DOWN[6]) {
                JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                        JjkoaNetwork.ActionPacket.FLAME_ARROW_RELEASE, CombatMode.isActive()));
            }
            boolean crouching = minecraft.options.keyShift.isDown();
            if (down[0] && crouching) {
                if (!WAS_DOWN[0]) {
                    gridHoldTicks = 1;
                    gridStreamTimer = 0;
                } else if (gridHoldTicks < 100) {
                    if (++gridStreamTimer >= 3) {
                        JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                                JjkoaNetwork.ActionPacket.GRID_STREAM_DISMANTLE, CombatMode.isActive()));
                        // 连续网格斩的下坠缓冲只在这里续期：真的发出发射包才算在连发，
                        // 不再用"按住 + 咒力 + 次数"去推断（上一版就是这么推断的，实际没生效）。
                        markStreamGlide();
                        gridStreamTimer = 0;
                    }
                    gridHoldTicks++;
                }
            } else {
                gridHoldTicks = 0;
                gridStreamTimer = 0;
            }
            boolean backing = minecraft.options.keyDown.isDown();
            boolean dismantleKeyDown = extraKeys[0].isDown();
            // Presses are captured by Forge's keyboard/mouse input events. This avoids
            // losing the one-tick edge while keeping the current configurable mapping.
            boolean dismantlePressed = pendingDismantlePresses > 0;
            pendingDismantlePresses = 0;
            if (dismantlePressed) {
                if (crouching && backing) {
                    sendDismantle(JjkoaNetwork.ActionPacket.BACKFLIP_DISMANTLE, false);
                    pulseCastShake();
                    holdTicks = 100;
                } else if (crouching) {
                    if (holdTicks < 100) {
                        sendDismantle(JjkoaNetwork.ActionPacket.STREAM_DISMANTLE, false);
                        holdTicks++;
                    }
                } else if (!crouching) {
                    sendDismantle(JjkoaNetwork.ActionPacket.DISMANTLE, false);
                    pulseCastShake();
                }
            }
            if (dismantleKeyDown && crouching && !backing) {
                if (!dismantlePressed && holdTicks < 100) {
                    sendDismantle(JjkoaNetwork.ActionPacket.STREAM_DISMANTLE, false);
                    holdTicks++;
                }
            } else if (!dismantleKeyDown) {
                holdTicks = 0;
            }
            boolean eightDown = extraKeys[1].isDown();
            boolean movingForward = minecraft.options.keyUp.isDown();
            if (eightDown && crouching && movingForward) {
                if (!eightComboUsed) {
                    JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                            JjkoaNetwork.ActionPacket.EIGHT_TELEPORT, CombatMode.isActive()));
                    CombatSkills.activateExtra(1);
                    eightComboUsed = true;
                }
            } else if (eightDown) {
                // One packet per tick keeps the 3x3x3 hitbox active exactly while X is held.
                JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                        JjkoaNetwork.ActionPacket.EIGHT_HOLD, CombatMode.isActive()));
                if (!eightComboUsed) CombatSkills.activateExtra(1);
                eightComboUsed = false;
            } else {
                eightComboUsed = false;
            }
            // C 键切换咒力流动；它不再触发旧的第三个空技能键。
            if (extraKeys[2].consumeClick()) {
                minecraft.player.displayClientMessage(Component.literal("[咒力流动]"), true);
                JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                        JjkoaNetwork.ActionPacket.TOGGLE_FLOW, CombatMode.isActive()));
            }
            // V 已经由上面的按住型反转术式逻辑处理，不能再作为旧的“技能键 4”触发一次。
            System.arraycopy(down, 0, WAS_DOWN, 0, down.length);
        }

        @SubscribeEvent
        public static void renderTick(TickEvent.RenderTickEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (event.phase == TickEvent.Phase.START) {
                applyCameraShake(minecraft);
            } else {
                restoreCameraShake(minecraft);
            }
        }

        /** 灶·开爆炸的屏幕轻微颤动：本渲染帧临时给视角叠加高频小幅抖动，渲染结束即还原，
         *  不影响发给服务端的真实朝向（移动包只在 tick 阶段发送）。 */
        private static float shakeX;
        private static float shakeY;
        private static long shakeFrames;

        private static void applyCameraShake(Minecraft minecraft) {
            if (shakeTicks <= 0 || minecraft.player == null || minecraft.level == null) return;
            shakeFrames++;
            // 连续解的持续震动要比一次性震荡更轻（振幅按比例缩小）；
            // 一次性震荡用 shakeScale：重击为中度（1.75），其余保持轻微档 1.0。
            double amp = streamHold ? STREAM_SHAKE_SCALE : shakeScale;
            double t = shakeFrames * 1.3D;
            if (directionalShake) {
                // 乱解的方向性震动：单一高频振荡沿服务端给定的方向矢量分解，
                // 每一刀换一个方向（四刀四象限），观感是"往不同方向被各甩一下"。
                double osc = (Math.sin(t * 3.1D) * 0.30D + Math.sin(t * 5.7D + 0.7D) * 0.11D) * amp;
                shakeX = (float) (osc * shakeDirX);
                shakeY = (float) (osc * shakeDirY);
            } else {
                shakeX = (float) ((Math.sin(t * 0.91D) * 0.30D + Math.sin(t * 2.31D + 0.7D) * 0.11D) * amp);
                shakeY = (float) ((Math.cos(t * 1.13D) * 0.30D + Math.sin(t * 1.83D + 1.1D) * 0.11D) * amp);
            }
            minecraft.player.setXRot(minecraft.player.getXRot() + shakeX);
            minecraft.player.setYRot(minecraft.player.getYRot() + shakeY);
        }

        private static void restoreCameraShake(Minecraft minecraft) {
            if (minecraft.player == null) return;
            if (shakeX != 0.0F) {
                minecraft.player.setXRot(minecraft.player.getXRot() - shakeX);
                shakeX = 0.0F;
            }
            if (shakeY != 0.0F) {
                minecraft.player.setYRot(minecraft.player.getYRot() - shakeY);
                shakeY = 0.0F;
            }
        }

        /**
         * 灶·开蓄力期间的拉弓动作：只要该玩家面前悬着未发射的火焰箭（服务端
         * 蓄力状态在客户端的可见投影），就把双臂覆盖成原版拉弓姿势。
         * RenderPlayerEvent.Pre 在 PlayerRenderer 算完原版姿势之后、模型
         * setupAnim 应用姿势之前触发，是 1.20.1 上最干净的注入点；
         * 第三人称（含 F5 自己）与其他玩家都能看到，松手箭矢发射即恢复。
         */
        @SubscribeEvent
        public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
            Player chargingPlayer = event.getEntity();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return;
            if (minecraft.level.getEntitiesOfClass(FlameArrowEntity.class,
                    chargingPlayer.getBoundingBox().inflate(8.0D),
                    arrow -> !arrow.isLaunched() && arrow.getOwner() == chargingPlayer).isEmpty()) return;
            PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
            model.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            model.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
        }

        @SubscribeEvent
        public static void renderSlicingLines(RenderLivingEvent.Post<?, ?> event) {
            LivingEntity entity = event.getEntity();
            if (!entity.hasEffect(GeneratedMod.SLICING_EFFECT.get())) return;
            PoseStack pose = event.getPoseStack();
            MultiBufferSource buffer = event.getMultiBufferSource();
            VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
            pose.pushPose();
            pose.translate(0.0D, entity.getBbHeight() * 0.55D, 0.0D);
            float radius = Math.max(0.28F, entity.getBbWidth() * 0.5F + 0.025F);
            drawCutLine(pose, consumer, -radius, 0.10F, radius, radius, 0.10F, radius,
                    20, 20, 20, 255);
            drawCutLine(pose, consumer, -radius, -0.10F, -radius, radius, -0.10F, -radius,
                    245, 245, 245, 255);
            drawCutLine(pose, consumer, radius, 0.16F, -0.20F, radius, 0.16F, 0.20F,
                    20, 20, 20, 255);
            drawCutLine(pose, consumer, -radius, -0.16F, -0.20F, -radius, -0.16F, 0.20F,
                    245, 245, 245, 255);
            pose.popPose();
        }

        private static void drawCutLine(PoseStack pose, VertexConsumer consumer,
                                        float x1, float y1, float z1, float x2, float y2, float z2,
                                        int red, int green, int blue, int alpha) {
            Matrix4f matrix = pose.last().pose();
            consumer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha)
                    .normal(0.0F, 1.0F, 0.0F).endVertex();
            consumer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha)
                    .normal(0.0F, 1.0F, 0.0F).endVertex();
        }

        private static void sendDismantle(int action) {
            sendDismantle(action, false);
        }

        private static void sendDismantle(int action, boolean mainSkill) {
            // Include the client combat state so the server can validate and apply the
            // action atomically instead of relying on a previous toggle packet arriving first.
            JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(action, CombatMode.isActive()));
            if (mainSkill) CombatSkills.activate(0);
            else CombatSkills.activateExtra(0);
            // 连续解的每一发都真的发出了包：下坠缓冲就在这里续期。普通解与后撤解是
            // 一次性技能，不参与坠落减速，所以不续期。
            if (action == JjkoaNetwork.ActionPacket.STREAM_DISMANTLE) markStreamGlide();
        }

        private static void sendDomainRelease() {
            JjkoaNetwork.send(new JjkoaNetwork.ActionPacket(
                    JjkoaNetwork.ActionPacket.DOMAIN_RELEASE, CombatMode.isActive()));
            if (domainActive) CombatSkills.activate(7);
        }

        private static boolean[] readNumberKeys(Minecraft minecraft) {
            boolean[] result = new boolean[NUMBERS.length];
            long window = minecraft.getWindow().getWindow();
            for (int i = 0; i < NUMBERS.length; i++) result[i] = InputConstants.isKeyDown(window, NUMBERS[i]);
            return result;
        }
    }

    private static void syncHotbarMappings(Minecraft minecraft) {
        if (minecraft.options == null) return;
        KeyMapping[] hotbarSlots = minecraft.options.keyHotbarSlots;
        boolean suppress = CombatMode.isActive();
        if (suppress && !hotbarMappingsSuppressed) {
            savedHotbarKeys = new InputConstants.Key[hotbarSlots.length];
            for (int i = 0; i < hotbarSlots.length; i++) {
                savedHotbarKeys[i] = hotbarSlots[i].getKey();
                hotbarSlots[i].setKey(InputConstants.UNKNOWN);
            }
            KeyMapping.resetMapping();
            hotbarMappingsSuppressed = true;
        } else if (!suppress && hotbarMappingsSuppressed) {
            if (savedHotbarKeys != null) {
                for (int i = 0; i < hotbarSlots.length && i < savedHotbarKeys.length; i++) {
                    hotbarSlots[i].setKey(savedHotbarKeys[i]);
                }
            }
            KeyMapping.resetMapping();
            savedHotbarKeys = null;
            hotbarMappingsSuppressed = false;
        }
    }

    private static void renderOverlay(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics,
                                      float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !CombatMode.isUnlocked(minecraft.player)) return;
        if (!CombatMode.isActive()) return;
        int box = Math.max(16, Math.min(22, height / 24));
        int gap = 1;
        int mainX = width - box - 8;
        int top = height - box * 8 - gap * 7 - 8;
        int extraX = mainX - box - gap;
        for (int i = 0; i < 8; i++) drawSlot(graphics, mainX, top + i * (box + gap), box,
                Integer.toString(i + 1), CombatSkills.isCoolingDown(i));
        for (int i = 0; i < 4; i++) drawSlot(graphics, extraX, top + (i + 4) * (box + gap), box,
                extraKeys == null ? "" : extraKeys[i].getTranslatedKeyMessage().getString(),
                CombatSkills.isCoolingDown(8 + i));
        drawEnergyBar(graphics, width, height, extraX, top, box, gap);
        if (ClientEvents.domainHoldTicks > 0 && !domainActive) {
            int shownTicks = Math.min(ClientEvents.domainHoldTicks, DOMAIN_CHARGE_TICKS);
            int shownTenths = shownTicks * 10 / 20;
            int totalTenths = DOMAIN_CHARGE_TICKS * 10 / 20;
            String text = "领域展开 " + shownTenths / 10 + "." + shownTenths % 10
                    + "/" + totalTenths / 10 + "." + totalTenths % 10 + "秒";
            int textWidth = Minecraft.getInstance().font.width(text);
            // 面板整体右对齐贴屏幕右侧；进度条宽度恒等于面板宽度并按比例填充，
            // 不会再穿出血色底框或越过屏幕边界。
            int boxLeft = width - textWidth - 10;
            int boxRight = width - 6;
            int textX = boxLeft + 2;
            int textY = Math.max(4, top - 18);
            graphics.fill(boxLeft, textY - 2, boxRight, textY + 11, 0xB0100D12);
            int filled = Math.max(0, Math.min(boxRight - boxLeft,
                    (boxRight - boxLeft) * shownTicks / DOMAIN_CHARGE_TICKS));
            graphics.fill(boxLeft, textY + 10, boxLeft + filled, textY + 11, 0xFFE03A4E);
            graphics.drawString(Minecraft.getInstance().font, text, textX, textY, 0xFFFFD4D8, true);
        }
    }

    /** 在右下角技能栏左侧绘制纯数字咒力，坐标随 GUI 缩放自然变化。 */
    private static void drawEnergyBar(GuiGraphics graphics, int width, int height,
                                      int extraX, int top, int box, int gap) {
        String text = "咒力:" + cursedEnergy + "/" + CursedEnergy.MAX_ENERGY;
        int textWidth = Minecraft.getInstance().font.width(text);
        int panelWidth = Math.max(box * 8, textWidth + 8);
        int left = Math.max(2, extraX - gap - panelWidth);
        int right = Math.min(width - 2, extraX - gap);
        int y = top + 7 * (box + gap);
        int textX = left + (right - left - textWidth) / 2;
        graphics.fill(left, y, right, y + box, 0xB0101018);
        graphics.fill(left, y, right, y + 1, energyFlowing ? 0xFF268CFF : 0xFF1760C9);
        graphics.drawString(Minecraft.getInstance().font, text, textX, y + (box - 8) / 2,
                energyFlowing ? 0xFF74CFFF : 0xFFB8DFFF, true);
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y, int size, String label, boolean cooling) {
        int fill = cooling ? 0xB015151B : 0xC026202A;
        graphics.fill(x, y, x + size, y + size, fill);
        graphics.fill(x, y, x + size, y + 1, 0xFFE03A4E);
        graphics.fill(x, y + size - 1, x + size, y + size, 0xFF5C1728);
        int textWidth = Minecraft.getInstance().font.width(label);
        graphics.drawString(Minecraft.getInstance().font, label, x + (size - textWidth) / 2,
                y + (size - 8) / 2, cooling ? 0xFF77737A : 0xFFFFD4D8, false);
    }
}
