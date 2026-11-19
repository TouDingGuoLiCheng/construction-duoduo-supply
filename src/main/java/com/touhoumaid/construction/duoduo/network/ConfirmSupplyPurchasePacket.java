package com.touhoumaid.construction.duoduo.network;

import com.touhoumaid.construction.duoduo.supply.SupplyListService;
import com.touhoumaid.construction.duoduo.supply.SupplyPurchaseService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ConfirmSupplyPurchasePacket {
    private final boolean itemMode;
    private final BlockPos pos;
    private final InteractionHand hand;
    private final String projectId;
    private final List<Integer> selectedIndices;

    public ConfirmSupplyPurchasePacket(
            boolean itemMode,
            BlockPos pos,
            InteractionHand hand,
            String projectId,
            List<Integer> selectedIndices) {
        this.itemMode = itemMode;
        this.pos = pos == null ? BlockPos.ZERO : pos;
        this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
        this.projectId = projectId == null ? "" : projectId;
        this.selectedIndices = selectedIndices == null ? List.of() : List.copyOf(selectedIndices);
    }

    public static void encode(ConfirmSupplyPurchasePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.itemMode);
        buf.writeBlockPos(packet.pos);
        buf.writeEnum(packet.hand);
        buf.writeUtf(packet.projectId, 128);
        buf.writeVarInt(packet.selectedIndices.size());
        for (int i : packet.selectedIndices) {
            buf.writeVarInt(i);
        }
    }

    public static ConfirmSupplyPurchasePacket decode(FriendlyByteBuf buf) {
        boolean itemMode = buf.readBoolean();
        BlockPos pos = buf.readBlockPos();
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        String projectId = buf.readUtf(128);
        int n = buf.readVarInt();
        List<Integer> indices = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            indices.add(buf.readVarInt());
        }
        return new ConfirmSupplyPurchasePacket(itemMode, pos, hand, projectId, indices);
    }

    public static void handle(ConfirmSupplyPurchasePacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            var sessionOpt = SupplyListService.resolve(
                    player, packet.itemMode, packet.pos, packet.hand, packet.projectId);
            if (sessionOpt.isEmpty()) {
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        SupplyPurchaseResultPacket.fail("message.construction_duoduo_supply.no_terminal"));
                return;
            }
            var session = sessionOpt.get();
            List<SupplyListService.Row> rows = SupplyListService.buildRows(player, session);
            SupplyPurchaseService.ResultSummary summary =
                    SupplyPurchaseService.purchase(player, session, rows, packet.selectedIndices);
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    SupplyPurchaseResultPacket.from(summary));
        });
        context.setPacketHandled(true);
    }
}
