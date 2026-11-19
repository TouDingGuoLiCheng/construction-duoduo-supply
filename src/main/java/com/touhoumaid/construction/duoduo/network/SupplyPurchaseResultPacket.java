package com.touhoumaid.construction.duoduo.network;

import com.touhoumaid.construction.duoduo.client.ClientSupplyScreens;
import com.touhoumaid.construction.duoduo.supply.SupplyPurchaseService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class SupplyPurchaseResultPacket {
    private final boolean ok;
    private final String messageKey;
    private final int boughtKinds;
    private final int boughtItems;
    private final long spent;
    private final int stoppedForCoins;
    private final int droppedItems;
    private final int expressQueued;

    private SupplyPurchaseResultPacket(
            boolean ok,
            String messageKey,
            int boughtKinds,
            int boughtItems,
            long spent,
            int stoppedForCoins,
            int droppedItems,
            int expressQueued) {
        this.ok = ok;
        this.messageKey = messageKey == null ? "" : messageKey;
        this.boughtKinds = boughtKinds;
        this.boughtItems = boughtItems;
        this.spent = spent;
        this.stoppedForCoins = stoppedForCoins;
        this.droppedItems = droppedItems;
        this.expressQueued = expressQueued;
    }

    public static SupplyPurchaseResultPacket fail(String messageKey) {
        return new SupplyPurchaseResultPacket(false, messageKey, 0, 0, 0, 0, 0, 0);
    }

    public static SupplyPurchaseResultPacket from(SupplyPurchaseService.ResultSummary summary) {
        return new SupplyPurchaseResultPacket(
                true,
                "message.construction_duoduo_supply.purchase_done",
                summary.boughtKinds(),
                summary.boughtItems(),
                summary.spent(),
                summary.stoppedForCoins(),
                summary.droppedItems(),
                summary.expressQueued());
    }

    public boolean ok() {
        return ok;
    }

    public String messageKey() {
        return messageKey;
    }

    public int boughtKinds() {
        return boughtKinds;
    }

    public int boughtItems() {
        return boughtItems;
    }

    public long spent() {
        return spent;
    }

    public int stoppedForCoins() {
        return stoppedForCoins;
    }

    public int droppedItems() {
        return droppedItems;
    }

    public int expressQueued() {
        return expressQueued;
    }

    public static void encode(SupplyPurchaseResultPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.ok);
        buf.writeUtf(packet.messageKey, 256);
        buf.writeVarInt(packet.boughtKinds);
        buf.writeVarInt(packet.boughtItems);
        buf.writeLong(packet.spent);
        buf.writeVarInt(packet.stoppedForCoins);
        buf.writeVarInt(packet.droppedItems);
        buf.writeVarInt(packet.expressQueued);
    }

    public static SupplyPurchaseResultPacket decode(FriendlyByteBuf buf) {
        return new SupplyPurchaseResultPacket(
                buf.readBoolean(),
                buf.readUtf(256),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readLong(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt());
    }

    public static void handle(SupplyPurchaseResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSupplyScreens.onPurchaseResult(packet)));
        context.setPacketHandled(true);
    }
}
