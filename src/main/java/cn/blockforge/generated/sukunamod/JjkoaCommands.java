package cn.blockforge.generated.sukunamod;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JjkoaCommands {
    private JjkoaCommands() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("jjkoa").requires(source -> source.hasPermission(2))
                .then(Commands.literal("nocooldown")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    JjkoaConfig.NO_COOLDOWN = BoolArgumentType.getBool(context, "enabled");
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "技能无冷却:" + JjkoaConfig.NO_COOLDOWN), true);
                                    return 1;
                                })))
                .then(Commands.literal("damage")
                        .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(
                                0.0D, JjkoaConfig.MAX_DAMAGE_MULTIPLIER))
                                .executes(context -> {
                                    JjkoaConfig.DAMAGE_MULTIPLIER = DoubleArgumentType.getDouble(context, "multiplier");
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "全局伤害倍率：" + JjkoaConfig.DAMAGE_MULTIPLIER
                                                    + "（解基础倍率 0.75）"), true);
                                    return 1;
                                })))
                .then(Commands.literal("terrain")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    JjkoaConfig.TERRAIN_DAMAGE = BoolArgumentType.getBool(context, "enabled");
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "方块破坏：" + (JjkoaConfig.TERRAIN_DAMAGE ? "开启" : "关闭")), true);
                                    return 1;
                                })))
                .then(Commands.literal("leaveclose")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    JjkoaConfig.LEAVE_CLOSES_DOMAIN = BoolArgumentType.getBool(context, "enabled");
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "离开领域立即结束领域："
                                                    + (JjkoaConfig.LEAVE_CLOSES_DOMAIN ? "开启" : "关闭")), true);
                                    return 1;
                                })))
                .then(Commands.literal("clear_mark")
                        .executes(context -> clearMark(context.getSource().getPlayerOrException(), context))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> clearMarkTarget(context))))
                .then(Commands.literal("refill")
                        .executes(context -> refillEnergy(context.getSource().getPlayerOrException(), context))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> refillEnergyTarget(context)))));
    }

    private static int refillEnergyTarget(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return refillEnergy(EntityArgument.getPlayer(context, "player"), context);
    }

    private static int refillEnergy(Player player,
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        if (!CombatMode.isUnlocked(player)) {
            context.getSource().sendFailure(Component.literal("该玩家尚未解锁宿傩能力"));
            return 0;
        }
        CursedEnergy.refill(player);
        if (player instanceof ServerPlayer serverPlayer) {
            JjkoaNetwork.syncEnergy(serverPlayer, CursedEnergy.get(player), CursedEnergy.isFlowing(player));
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "已回满咒力：" + player.getName().getString() + "（" + CursedEnergy.MAX_ENERGY + "/"
                        + CursedEnergy.MAX_ENERGY + "）"), true);
        return 1;
    }

    private static int clearMarkTarget(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return clearMark(EntityArgument.getPlayer(context, "player"), context);
    }

    private static int clearMark(Player player, com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        CombatMode.clearUnlock(player);
        if (player instanceof ServerPlayer serverPlayer) JjkoaNetwork.syncUnlock(serverPlayer);
        context.getSource().sendSuccess(() -> Component.literal(
                "已清除宿傩印记：" + player.getName().getString()), true);
        return 1;
    }
}
