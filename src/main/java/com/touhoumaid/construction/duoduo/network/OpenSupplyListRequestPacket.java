package com.touhoumaid.construction.duoduo.network;

import com.homestead.sellveg.currency.CurrencyHelper;
import com.touhoumaid.construction.duoduo.supply.SupplyListService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Supplier;

public final class OpenSupplyListRequestPacket {
    private final boolean itemMode;
    private final BlockPos pos;
    private final InteractionHand hand;
    private final String projectId;

    public OpenSupplyListRequestPacket(boolean itemMode, BlockPos pos, InteractionHand hand, String projectId) {
        this.itemMode = itemMode;
        this.pos = pos == null ? BlockPos.ZERO : pos;
        this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
        this.projectId = projectId == null ? "" : projectId;
    }

    public static void encode(OpenSupplyListRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.itemMode);
        buf.writeBlockPos(packet.pos);
        buf.writeEnum(packet.hand);
        buf.writeUtf(packet.projectId, 128);
    }

    public static OpenSupplyListRequestPacket decode(FriendlyByteBuf buf) {
        return new OpenSupplyListRequestPacket(
                buf.readBoolean(), buf.readBlockPos(), buf.readEnum(InteractionHand.class), buf.readUtf(128));
    }

    public static void handle(OpenSupplyListRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            var session = SupplyListService.resolve(
                    player, packet.itemMode, packet.pos, packet.hand, packet.projectId);
            String projectId = packet.projectId;
            List<SupplyListService.Row> rows = List.of();
            if (session.isPresent()) {
                projectId = session.get().project().id();
                rows = SupplyListService.buildRows(player, session.get());
            }
            long coins = CurrencyHelper.countCoins(player);
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SupplyListSyncPacket(
                            packet.itemMode, packet.pos, packet.hand, projectId, coins, rows));
        });
        context.setPacketHandled(true);
    }
}
