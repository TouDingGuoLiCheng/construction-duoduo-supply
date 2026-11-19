package com.touhoumaid.construction.duoduo.network;

import com.touhoumaid.construction.duoduo.ConstructionDuoduoSupplyMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ConstructionDuoduoSupplyMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int id;

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(OpenSupplyListRequestPacket.class, id++)
                .decoder(OpenSupplyListRequestPacket::decode)
                .encoder(OpenSupplyListRequestPacket::encode)
                .consumerMainThread(OpenSupplyListRequestPacket::handle)
                .add();
        CHANNEL.messageBuilder(SupplyListSyncPacket.class, id++)
                .decoder(SupplyListSyncPacket::decode)
                .encoder(SupplyListSyncPacket::encode)
                .consumerMainThread(SupplyListSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(ConfirmSupplyPurchasePacket.class, id++)
                .decoder(ConfirmSupplyPurchasePacket::decode)
                .encoder(ConfirmSupplyPurchasePacket::encode)
                .consumerMainThread(ConfirmSupplyPurchasePacket::handle)
                .add();
        CHANNEL.messageBuilder(SupplyPurchaseResultPacket.class, id++)
                .decoder(SupplyPurchaseResultPacket::decode)
                .encoder(SupplyPurchaseResultPacket::encode)
                .consumerMainThread(SupplyPurchaseResultPacket::handle)
                .add();
    }
}
