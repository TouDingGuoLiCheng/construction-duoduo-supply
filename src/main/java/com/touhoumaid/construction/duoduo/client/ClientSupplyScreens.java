package com.touhoumaid.construction.duoduo.client;

import com.touhoumaid.construction.duoduo.network.SupplyListSyncPacket;
import com.touhoumaid.construction.duoduo.network.SupplyPurchaseResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientSupplyScreens {
    private ClientSupplyScreens() {
    }

    public static void openList(SupplyListSyncPacket packet) {
        Minecraft.getInstance().setScreen(new SupplyListScreen(packet));
    }

    public static void onPurchaseResult(SupplyPurchaseResultPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!packet.ok()) {
            mc.player.displayClientMessage(Component.translatable(packet.messageKey()), true);
            return;
        }
        mc.player.displayClientMessage(Component.translatable(
                packet.messageKey(),
                packet.boughtKinds(),
                packet.boughtItems(),
                packet.spent(),
                packet.stoppedForCoins(),
                packet.droppedItems(),
                packet.expressQueued()), true);
        if (mc.screen instanceof SupplyListScreen) {
            mc.setScreen(null);
        }
    }
}
