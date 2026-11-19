package com.touhoumaid.construction.duoduo.supply;

import com.homestead.sellveg.api.MallPurchaseApi;
import com.homestead.sellveg.currency.CurrencyHelper;
import com.touhoumaid.construction.api.ConstructionSupplyApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Buys selected shortage rows in order: vault → player inventory → express → drop at feet.
 * Stops when the wallet cannot afford the next unit (partial buys within a row still apply).
 */
public final class SupplyPurchaseService {
    private SupplyPurchaseService() {
    }

    public record ResultSummary(
            int boughtKinds,
            int boughtItems,
            long spent,
            int stoppedForCoins,
            int droppedItems,
            int expressQueued,
            int skippedUnpurchasable) {
    }

    public static ResultSummary purchase(
            ServerPlayer player,
            SupplyListService.TerminalSession session,
            List<SupplyListService.Row> allRows,
            List<Integer> selectedIndices) {
        int boughtKinds = 0;
        int boughtItems = 0;
        long spent = 0;
        int stoppedForCoins = 0;
        int droppedItems = 0;
        int expressQueued = 0;
        int skippedUnpurchasable = 0;

        for (int index : selectedIndices) {
            if (index < 0 || index >= allRows.size()) {
                continue;
            }
            SupplyListService.Row row = allRows.get(index);
            if (!row.purchasable()) {
                skippedUnpurchasable++;
                continue;
            }
            Item item = SupplyListService.resolveItem(row.purchaseItemId());
            if (item == Items.AIR) {
                skippedUnpurchasable++;
                continue;
            }
            if (CurrencyHelper.countCoins(player) < row.unitPrice()) {
                stoppedForCoins++;
                break;
            }

            List<ItemStack> overflow = new ArrayList<>();
            MallPurchaseApi.PurchaseResult bought = MallPurchaseApi.purchase(
                    player, item, row.need(), stack -> {
                        int left = ConstructionSupplyApi.deposit(session.vault(), stack);
                        if (left <= 0) {
                            return 0;
                        }
                        ItemStack rest = stack.copy();
                        rest.setCount(left);
                        if (player.getInventory().add(rest)) {
                            return 0;
                        }
                        overflow.add(rest.copy());
                        return rest.getCount();
                    });

            if (bought.status() == MallPurchaseApi.Status.NOT_ENOUGH_COINS && bought.boughtCount() == 0) {
                stoppedForCoins++;
                break;
            }
            if (bought.status() != MallPurchaseApi.Status.OK || bought.boughtCount() <= 0) {
                continue;
            }

            session.saveVault().run();
            boughtKinds++;
            boughtItems += bought.boughtCount();
            spent += bought.spent();

            if (!overflow.isEmpty()) {
                MallPurchaseApi.Status ship = MallPurchaseApi.enqueueExpress(player, overflow);
                if (ship == MallPurchaseApi.Status.OK) {
                    expressQueued += overflow.stream().mapToInt(ItemStack::getCount).sum();
                } else {
                    for (ItemStack drop : overflow) {
                        droppedItems += dropAtFeet(player, drop);
                    }
                }
            }
        }

        return new ResultSummary(
                boughtKinds, boughtItems, spent, stoppedForCoins, droppedItems, expressQueued, skippedUnpurchasable);
    }

    private static int dropAtFeet(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int count = stack.getCount();
        ItemEntity entity = new ItemEntity(
                player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack.copy());
        entity.setPickUpDelay(20);
        player.level().addFreshEntity(entity);
        return count;
    }
}
