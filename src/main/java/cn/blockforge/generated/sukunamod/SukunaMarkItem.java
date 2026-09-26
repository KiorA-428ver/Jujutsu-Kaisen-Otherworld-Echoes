package cn.blockforge.generated.sukunamod;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SukunaMarkItem extends Item {
    public SukunaMarkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CombatMode.unlock(player);
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            JjkoaNetwork.syncUnlock(serverPlayer);
            JjkoaNetwork.syncEnergy(serverPlayer, CursedEnergy.get(player), CursedEnergy.isFlowing(player));
            stack.shrink(1);
        }
        player.displayClientMessage(Component.translatable("message.sukunamod.mark_unlocked"), true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
