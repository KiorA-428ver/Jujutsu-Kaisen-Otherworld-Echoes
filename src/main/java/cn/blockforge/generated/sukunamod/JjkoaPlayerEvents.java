package cn.blockforge.generated.sukunamod;

import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JjkoaPlayerEvents {
    private static final String UNLOCK_SYNC_PENDING_TAG = "sukunamod_unlock_sync_pending";

    private JjkoaPlayerEvents() { }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        boolean unlocked = event.getOriginal().getPersistentData().getBoolean(CombatMode.UNLOCKED_TAG);
        boolean active = event.getOriginal().getPersistentData().getBoolean(CombatMode.ACTIVE_TAG);
        boolean flowing = event.getOriginal().getPersistentData().getBoolean(CursedEnergy.FLOW_TAG);
        event.getEntity().getPersistentData().putBoolean(CombatMode.UNLOCKED_TAG, unlocked);
        // 死亡时战斗模式关闭；非死亡克隆（例如换维度）完整保留战斗模式。
        event.getEntity().getPersistentData().putBoolean(CombatMode.ACTIVE_TAG,
                !event.isWasDeath() && active && unlocked);
        if (unlocked) {
            // 死亡重生时回满咒力；非死亡克隆继续保留当前值。
            if (event.isWasDeath()) {
                CursedEnergy.refill(event.getEntity());
            } else {
                int energy = CursedEnergy.get(event.getOriginal());
                event.getEntity().getPersistentData().putInt(CursedEnergy.ENERGY_TAG, energy);
                event.getEntity().getPersistentData().putLong(CursedEnergy.LAST_RECOVERY_TAG,
                        event.getEntity().level().getGameTime());
            }
        }
        CursedEnergy.setFlow(event.getEntity(), !event.isWasDeath() && unlocked && flowing);
        // The client still points at the old player when PlayerEvent.Clone fires.
        // Defer the packet until the replacement player has started ticking.
        event.getEntity().getPersistentData().putBoolean(UNLOCK_SYNC_PENDING_TAG, true);
    }

    /** 换维度不会重新给玩家发能力包，显式同步一次持久能力、咒力与战斗状态。 */
    @SubscribeEvent
    public static void changedDimension(PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (CombatMode.isUnlocked(player)) {
            // 维度切换后实体实例会被替换；重新应用持久状态对应的属性修饰，
            // 并同时同步解锁、战斗模式和咒力，避免客户端 HUD 或服务端能力短暂丢失。
            boolean active = player.getPersistentData().getBoolean(CombatMode.ACTIVE_TAG);
            boolean flowing = CursedEnergy.isFlowing(player);
            CombatMode.setServerActive(player, active);
            CursedEnergy.setFlow(player, flowing);
            JjkoaNetwork.syncUnlock(player);
            JjkoaNetwork.syncEnergy(player, CursedEnergy.get(player), flowing);
        }
    }

    @SubscribeEvent
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (CombatMode.isUnlocked(player)) CursedEnergy.refill(player);
            JjkoaNetwork.syncUnlock(player);
            if (CombatMode.isUnlocked(player)) JjkoaNetwork.syncEnergy(player,
                    CursedEnergy.get(player), CursedEnergy.isFlowing(player));
            // 领域是纯内存状态，重启世界后服务端必然没有玩家的领域；但"退出世界"
            // 时发给断线玩家的关闭包多半已经送不到，客户端的领域标记会跨存档残留。
            // 登录时权威地补一次"领域已关闭 + 视觉复位"，重进世界即可再次展开领域。
            JjkoaNetwork.syncDomainState(player, false);
            JjkoaNetwork.sendDomainVisual(player, JjkoaNetwork.DOMAIN_VISUAL_OFF);
        }
    }

    /**
     * 界主下线：领域随人一起关掉（神龛清空 + 圈内玩家收到 OFF 包）。
     * 否则领域只在界主自己的 tick 里维护，人一走就没人收摊，
     * 留在圈里的玩家会永久卡在暗红雾/暗化/色差后处理中。
     */
    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DomainSkill.onOwnerDisconnect(event.getEntity());
        ChaosDismantleSkill.onOwnerDisconnect(event.getEntity());
    }

    /**
     * 早期版本会把神龛写进存档，玩家世界里可能还留着这类"孤儿"神龛
     * （领域本身不跨重启存在）。区块加载它们时直接拦掉：不进气泡、不同步、
     * 也不占实体数。本版起神龛已声明不入库存，这里是给旧存档补的清理。
     */
    @SubscribeEvent
    public static void onShrineLoadedFromDisk(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ShrineEntity && event.loadedFromDisk()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void livingDamage(LivingDamageEvent event) {
        if (!(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel)
                || event.getEntity().isDeadOrDying()) return;
        LivingEntity target = event.getEntity();
        Entity source = event.getSource().getEntity();
        ServerPlayer attacker = source instanceof ServerPlayer player ? player : null;
        if (attacker == null && source instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer player) attacker = player;
        if (attacker == null || attacker == target) return;
        PursuitSkill.markHit(attacker, target);
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer
                && !event.player.level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.player;
            if (player.getPersistentData().getBoolean(UNLOCK_SYNC_PENDING_TAG)) {
                JjkoaNetwork.syncUnlock(player);
                if (CombatMode.isUnlocked(player)) {
                    JjkoaNetwork.syncEnergy(player, CursedEnergy.get(player), CursedEnergy.isFlowing(player));
                }
                player.getPersistentData().remove(UNLOCK_SYNC_PENDING_TAG);
            }
            CursedEnergy.tick(player);
            EightSkill.tick(event.player);
            DomainSkill.tick(event.player);
            ComboSkill.tick(event.player);
            PursuitSkill.tick(event.player);
            ChaosDismantleSkill.tick(event.player);
            FlameArrowSkill.tick(event.player);
            ReverseTechnique.tick(event.player);
            SkillCooldowns.tick(event.player);
        }
    }
}
