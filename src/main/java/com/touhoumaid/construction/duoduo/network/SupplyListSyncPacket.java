package com.touhoumaid.construction.duoduo.network;

import com.touhoumaid.construction.duoduo.client.ClientSupplyScreens;
import com.touhoumaid.construction.duoduo.supply.SupplyListService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class SupplyListSyncPacket {
    private final boolean itemMode;
    private final BlockPos pos;
    private final InteractionHand hand;
    private final String projectId;
    private final long coins;
    private final List<SupplyListService.Row> rows;

    public SupplyListSyncPacket(
            boolean itemMode,
            BlockPos pos,
            InteractionHand hand,
            String projectId,
            long coins,
            List<SupplyListService.Row> rows) {
        this.itemMode = itemMode;
        this.pos = pos == null ? BlockPos.ZERO : pos;
        this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
        this.projectId = projectId == null ? "" : projectId;
        this.coins = coins;
        this.rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public boolean itemMode() {
        return itemMode;
    }

    public BlockPos pos() {
        return pos;
    }

    public InteractionHand hand() {
        return hand;
    }

    public String projectId() {
        return projectId;
    }

    public long coins() {
        return coins;
    }

    public List<SupplyListService.Row> rows() {
        return rows;
    }

    public static void encode(SupplyListSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.itemMode);
        buf.writeBlockPos(packet.pos);
        buf.writeEnum(packet.hand);
        buf.writeUtf(packet.projectId, 128);
        buf.writeLong(packet.coins);
        buf.writeVarInt(packet.rows.size());
        for (SupplyListService.Row row : packet.rows) {
            buf.writeUtf(row.blockId(), 256);
            buf.writeUtf(row.targetItemId(), 256);
            buf.writeUtf(row.purchaseItemId(), 256);
            buf.writeVarInt(row.need());
            buf.writeVarInt(row.unitPrice());
            buf.writeBoolean(row.purchasable());
            buf.writeBoolean(row.ingredientBuy());
        }
    }

    public static SupplyListSyncPacket decode(FriendlyByteBuf buf) {
        boolean itemMode = buf.readBoolean();
        BlockPos pos = buf.readBlockPos();
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        String projectId = buf.readUtf(128);
        long coins = buf.readLong();
        int n = buf.readVarInt();
        List<SupplyListService.Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new SupplyListService.Row(
                    buf.readUtf(256),
                    buf.readUtf(256),
                    buf.readUtf(256),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean()));
        }
        return new SupplyListSyncPacket(itemMode, pos, hand, projectId, coins, rows);
    }

    public static void handle(SupplyListSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSupplyScreens.openList(packet)));
        context.setPacketHandled(true);
    }
}
