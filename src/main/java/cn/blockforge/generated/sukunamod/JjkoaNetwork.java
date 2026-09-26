package cn.blockforge.generated.sukunamod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public final class JjkoaNetwork {
    private static final String PROTOCOL = "1";
    /** 领域视觉同步：关闭（雾/天空褪色、移除迷雾与后处理）。 */
    public static final int DOMAIN_VISUAL_OFF = 0;
    /** 领域视觉同步：前摇开始，1.5 秒内雾与天空渐变成暗红。 */
    public static final int DOMAIN_VISUAL_PRECAST = 1;
    /** 领域视觉同步：领域已开启，满额暗红 + 迷雾收缩 + 震颤/暗化/色差。 */
    public static final int DOMAIN_VISUAL_ACTIVE = 2;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GeneratedMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int nextId;

    private JjkoaNetwork() { }

    public static void init() {
        CHANNEL.registerMessage(nextId++, ActionPacket.class, ActionPacket::encode,
                ActionPacket::decode, ActionPacket::handle);
        CHANNEL.registerMessage(nextId++, UnlockSyncPacket.class, UnlockSyncPacket::encode,
                UnlockSyncPacket::decode, UnlockSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, DomainClosePacket.class, DomainClosePacket::encode,
                DomainClosePacket::decode, DomainClosePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, DomainStatePacket.class, DomainStatePacket::encode,
                DomainStatePacket::decode, DomainStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, EnergySyncPacket.class, EnergySyncPacket::encode,
                EnergySyncPacket::decode, EnergySyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, FlameBurstPacket.class, FlameBurstPacket::encode,
                FlameBurstPacket::decode, FlameBurstPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, DomainVisualPacket.class, DomainVisualPacket::encode,
                DomainVisualPacket::decode, DomainVisualPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, ShakePacket.class, ShakePacket::encode,
                ShakePacket::decode, ShakePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, SlashImpactPacket.class, SlashImpactPacket::encode,
                SlashImpactPacket::decode, SlashImpactPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, ChaosStrikePacket.class, ChaosStrikePacket::encode,
                ChaosStrikePacket::decode, ChaosStrikePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void send(ActionPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void syncUnlock(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new UnlockSyncPacket(CombatMode.isUnlocked(player), CombatMode.isServerActive(player)));
    }

    public static void syncDomainClose(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DomainClosePacket());
    }

    public static void syncDomainState(ServerPlayer player, boolean active) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DomainStatePacket(active));
    }

    public static void syncEnergy(ServerPlayer player, int energy, boolean flowing) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EnergySyncPacket(energy, flowing));
    }

    /** 灶·开爆炸：向玩家推送屏幕颤动 tick 数；冲击帧数大于 0 表示"第五发闪光放到最大"，客户端立即截屏播冲击帧。 */
    public static void sendFlameBurst(ServerPlayer player, int shakeTicks, int impactFrames) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new FlameBurstPacket(shakeTicks, impactFrames));
    }

    /** 领域视觉同步包：0 关闭 / 1 前摇渐红 / 2 领域开启（迷雾+震颤+暗化+色差）。 */
    public static void sendDomainVisual(ServerPlayer player, int mode) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DomainVisualPacket(mode));
    }

    /** 重击等技能的一次性震屏：向施放者推送持续 tick 数与振幅百分比（100=普通解的轻微档）。 */
    public static void sendShake(ServerPlayer player, int ticks, int scalePercent) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShakePacket(ticks, scalePercent));
    }

    /**
     * 世界斩脱手瞬间的黑白冲击帧：A 段黑白反色 invertedMs 毫秒，B 段正常黑白 normalMs 毫秒，
     * 每段起播时各来一次持续 shakeTicks tick、振幅 shakePercent% 的震屏（中度=175）。
     */
    public static void sendSlashImpact(ServerPlayer player, int shakeTicks, int shakePercent,
                                       int invertedMs, int normalMs) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SlashImpactPacket(shakeTicks, shakePercent, invertedMs, normalMs));
    }

    /**
     * 乱解每一刀：向施放者推送一次沿 degrees 方向、percent% 振幅、持续 ticks 刻的
     * 方向性震屏，并把"柔和暗化 + 中度色差"后处理续期到 visualTicks 刻。
     */
    public static void sendChaosStrike(ServerPlayer player, int ticks, int percent,
                                       float degrees, int visualTicks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ChaosStrikePacket(ticks, percent, degrees, visualTicks));
    }

    public static final class ChaosStrikePacket {
        private final int ticks;
        private final int percent;
        private final float degrees;
        private final int visualTicks;

        private ChaosStrikePacket(int ticks, int percent, float degrees, int visualTicks) {
            this.ticks = ticks;
            this.percent = percent;
            this.degrees = degrees;
            this.visualTicks = visualTicks;
        }

        private static void encode(ChaosStrikePacket packet, net.minecraft.network.FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.ticks);
            buffer.writeVarInt(packet.percent);
            buffer.writeFloat(packet.degrees);
            buffer.writeVarInt(packet.visualTicks);
        }

        private static ChaosStrikePacket decode(net.minecraft.network.FriendlyByteBuf buffer) {
            return new ChaosStrikePacket(buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readFloat(), buffer.readVarInt());
        }

        private static void handle(ChaosStrikePacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> SukunaClient.applyChaosStrike(packet.ticks, packet.percent,
                            packet.degrees, packet.visualTicks)));
            context.setPacketHandled(true);
        }
    }

    public static final class SlashImpactPacket {
        private final int shakeTicks;
        private final int shakePercent;
        private final int invertedMs;
        private final int normalMs;

        private SlashImpactPacket(int shakeTicks, int shakePercent, int invertedMs, int normalMs) {
            this.shakeTicks = shakeTicks;
            this.shakePercent = shakePercent;
            this.invertedMs = invertedMs;
            this.normalMs = normalMs;
        }

        private static void encode(SlashImpactPacket packet, net.minecraft.network.FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.shakeTicks);
            buffer.writeVarInt(packet.shakePercent);
            buffer.writeVarInt(packet.invertedMs);
            buffer.writeVarInt(packet.normalMs);
        }

        private static SlashImpactPacket decode(net.minecraft.network.FriendlyByteBuf buffer) {
            return new SlashImpactPacket(buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt());
        }

        private static void handle(SlashImpactPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> SukunaClient.applySlashImpact(packet.shakeTicks, packet.shakePercent,
                            packet.invertedMs, packet.normalMs)));
            context.setPacketHandled(true);
        }
    }

    public static final class ShakePacket {
        private final int ticks;
        private final int scalePercent;

        private ShakePacket(int ticks, int scalePercent) {
            this.ticks = ticks;
            this.scalePercent = scalePercent;
        }

        private static void encode(ShakePacket packet, net.minecraft.network.FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.ticks);
            buffer.writeVarInt(packet.scalePercent);
        }

        private static ShakePacket decode(net.minecraft.network.FriendlyByteBuf buffer) {
            return new ShakePacket(buffer.readVarInt(), buffer.readVarInt());
        }

        private static void handle(ShakePacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> SukunaClient.applyShake(packet.ticks, packet.scalePercent)));
            context.setPacketHandled(true);
        }
    }

    public static final class DomainVisualPacket {
        private final int mode;

        private DomainVisualPacket(int mode) {
            this.mode = mode;
        }

        private static void encode(DomainVisualPacket packet, net.minecraft.network.FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.mode);
        }

        private static DomainVisualPacket decode(net.minecraft.network.FriendlyByteBuf buffer) {
            return new DomainVisualPacket(buffer.readVarInt());
        }

        private static void handle(DomainVisualPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> DomainVisuals.applyDomainVisual(packet.mode)));
            context.setPacketHandled(true);
        }
    }

    public static final class FlameBurstPacket {
        private final int shakeTicks;
        private final int impactFrames;

        private FlameBurstPacket(int shakeTicks, int impactFrames) {
            this.shakeTicks = shakeTicks;
            this.impactFrames = impactFrames;
        }

        private static void encode(FlameBurstPacket packet, net.minecraft.network.FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.shakeTicks);
            buffer.writeVarInt(packet.impactFrames);
        }

        private static FlameBurstPacket decode(net.minecraft.network.FriendlyByteBuf buffer) {
            return new FlameBurstPacket(buffer.readVarInt(), buffer.readVarInt());
        }

        private static void handle(FlameBurstPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> SukunaClient.applyFlameBurst(packet.shakeTicks, packet.impactFrames)));
            context.setPacketHandled(true);
        }
    }

    public static final class EnergySyncPacket {
        private final int energy;
        private final boolean flowing;

        private EnergySyncPacket(int energy, boolean flowing) {
            this.energy = energy;
            this.flowing = flowing;
        }

        private static void encode(EnergySyncPacket packet, net.minecraft.network.FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.energy);
            buffer.writeBoolean(packet.flowing);
        }

        private static EnergySyncPacket decode(net.minecraft.network.FriendlyByteBuf buffer) {
            return new EnergySyncPacket(buffer.readVarInt(), buffer.readBoolean());
        }

        private static void handle(EnergySyncPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> SukunaClient.applyEnergySync(packet.energy, packet.flowing)));
            context.setPacketHandled(true);
        }
    }

    public static final class DomainStatePacket {
        private final boolean active;

        private DomainStatePacket(boolean active) {
            this.active = active;
        }

        private static void encode(DomainStatePacket packet, net.minecraft.network.FriendlyByteBuf buffer) {
            buffer.writeBoolean(packet.active);
        }

        private static DomainStatePacket decode(net.minecraft.network.FriendlyByteBuf buffer) {
            return new DomainStatePacket(buffer.readBoolean());
        }

        private static void handle(DomainStatePacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> SukunaClient.setDomainActive(packet.active)));
            context.setPacketHandled(true);
        }
    }

    public static final class DomainClosePacket {
        private static void encode(DomainClosePacket packet, net.minecraft.network.FriendlyByteBuf buffer) { }
        private static DomainClosePacket decode(net.minecraft.network.FriendlyByteBuf buffer) { return new DomainClosePacket(); }
        private static void handle(DomainClosePacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> CombatSkills.domainClosed()));
            context.setPacketHandled(true);
        }
    }

    public static final class UnlockSyncPacket {
        private final boolean unlocked;
        private final boolean active;

        public UnlockSyncPacket(boolean unlocked, boolean active) {
            this.unlocked = unlocked;
            this.active = active;
        }

        private static void encode(UnlockSyncPacket packet, net.minecraft.network.FriendlyByteBuf buffer) {
            buffer.writeBoolean(packet.unlocked);
            buffer.writeBoolean(packet.active);
        }

        private static UnlockSyncPacket decode(net.minecraft.network.FriendlyByteBuf buffer) {
            return new UnlockSyncPacket(buffer.readBoolean(), buffer.readBoolean());
        }

        private static void handle(UnlockSyncPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> SukunaClient.applyUnlockSync(packet.unlocked, packet.active)));
            context.setPacketHandled(true);
        }
    }

    public static final class ActionPacket {
        public static final int TOGGLE_MODE = 0;
        public static final int DISMANTLE = 1;
        public static final int STREAM_DISMANTLE = 2;
        public static final int BACKFLIP_DISMANTLE = 3;
        public static final int GRID_DISMANTLE = 4;
        public static final int EIGHT_HOLD = 5;
        public static final int EIGHT_TELEPORT = 6;
        public static final int GRID_STREAM_DISMANTLE = 7;
        public static final int DOMAIN_EXPANSION = 8;
        public static final int COMBO_HOLD = 9;
        public static final int FLAME_ARROW_HOLD = 10;
        public static final int FLAME_ARROW_RELEASE = 11;
        public static final int REVERSE_HOLD = 12;
        public static final int DOMAIN_HOLD = 13;
        public static final int DOMAIN_RELEASE = 14;
        public static final int PURSUIT = 15;
        public static final int TOGGLE_FLOW = 16;
        public static final int WORLD_SLASH_HOLD = 17;
        public static final int WORLD_SLASH_RELEASE = 18;
        /** 技能5“重击”：面前 4x4x4、2.4 倍率伤害，中心放大两倍打击帧 + 施放者中度震屏。 */
        public static final int HEAVY_STRIKE = 19;
        /** 技能6“乱解”：以自身为中心 9x9x9 内约 1.1 秒七连斩，逐刀结算并回推方向性震屏。 */
        public static final int LUANJIE = 20;
        private final int action;
        private final boolean enabled;

        public ActionPacket(int action) {
            this(action, false);
        }

        public ActionPacket(int action, boolean enabled) {
            this.action = action;
            this.enabled = enabled;
        }

        private static void encode(ActionPacket packet, net.minecraft.network.FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.action);
            buffer.writeBoolean(packet.enabled);
        }

        private static ActionPacket decode(net.minecraft.network.FriendlyByteBuf buffer) {
            return new ActionPacket(buffer.readVarInt(), buffer.readBoolean());
        }

        private static void handle(ActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !CombatMode.isUnlocked(player)) return;
                if (packet.action == TOGGLE_MODE) {
                    CombatMode.setServerActive(player, packet.enabled);
                    if (!packet.enabled) WorldSlashSkill.reset(player);
                } else if (packet.action == FLAME_ARROW_RELEASE) {
                    // 松键包携带 enabled=false，不能被普通技能的 enabled 门控吞掉。
                    if (packet.enabled) CombatMode.setServerActive(player, true);
                    FlameArrowSkill.release(player);
                } else if (packet.action == DOMAIN_RELEASE) {
                    DomainSkill.releaseOrClose(player);
                } else if (packet.action == REVERSE_HOLD) {
                    if (packet.enabled) {
                        CombatMode.setServerActive(player, true);
                        ReverseTechnique.hold(player);
                    }
                } else if (packet.action == DOMAIN_HOLD) {
                    if (packet.enabled) {
                        CombatMode.setServerActive(player, true);
                        DomainSkill.hold(player);
                    }
                } else if (packet.action == TOGGLE_FLOW) {
                    if (packet.enabled) {
                        CombatMode.setServerActive(player, true);
                        CursedEnergy.toggleFlow(player);
                    }
                } else if (packet.action == WORLD_SLASH_HOLD) {
                    if (packet.enabled) {
                        CombatMode.setServerActive(player, true);
                        WorldSlashSkill.hold(player);
                    }
                } else if (packet.action == WORLD_SLASH_RELEASE) {
                    WorldSlashSkill.release(player);
                } else if (packet.enabled) {
                    // Skill packets carry the current combat state. Applying it in the
                    // same queued work item prevents a lost/out-of-order toggle from
                    // making normal and backflip casts silently disappear.
                    CombatMode.setServerActive(player, true);
                    if (packet.action == DISMANTLE) DismantleSkill.cast(player, DismantleSkill.NORMAL);
                    if (packet.action == STREAM_DISMANTLE) DismantleSkill.cast(player, DismantleSkill.STREAM);
                    if (packet.action == BACKFLIP_DISMANTLE) DismantleSkill.cast(player, DismantleSkill.BACKFLIP);
                    if (packet.action == GRID_DISMANTLE) DismantleSkill.cast(player, DismantleSkill.GRID);
                    if (packet.action == GRID_STREAM_DISMANTLE) DismantleSkill.cast(player, DismantleSkill.GRID_STREAM);
                    if (packet.action == EIGHT_HOLD) EightSkill.hold(player);
                    if (packet.action == EIGHT_TELEPORT) EightSkill.teleport(player);
                    if (packet.action == DOMAIN_EXPANSION) DomainSkill.toggle(player);
                    if (packet.action == PURSUIT) PursuitSkill.cast(player);
                    if (packet.action == COMBO_HOLD) ComboSkill.hold(player);
                    if (packet.action == HEAVY_STRIKE) HeavyStrikeSkill.cast(player);
                    if (packet.action == LUANJIE) ChaosDismantleSkill.cast(player);
                    if (packet.action == FLAME_ARROW_HOLD) FlameArrowSkill.hold(player);
                }
            });
            context.setPacketHandled(true);
        }
    }
}
