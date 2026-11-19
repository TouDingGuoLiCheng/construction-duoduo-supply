package com.touhoumaid.construction.duoduo.client;

import com.touhoumaid.construction.duoduo.ConstructionDuoduoSupplyMod;
import com.touhoumaid.construction.api.client.HubPage2ButtonRegistry;
import com.touhoumaid.construction.duoduo.network.ModNetwork;
import com.touhoumaid.construction.duoduo.network.OpenSupplyListRequestPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ConstructionDuoduoSupplyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> HubPage2ButtonRegistry.register(
                ConstructionDuoduoSupplyMod.MOD_ID + ":buy",
                "gui.construction_duoduo_supply.hub_buy",
                (terminal, projectId) -> ModNetwork.CHANNEL.sendToServer(new OpenSupplyListRequestPacket(
                        terminal.itemMode(),
                        terminal.pos(),
                        terminal.hand(),
                        projectId == null ? "" : projectId))));
    }
}
