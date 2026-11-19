package com.touhoumaid.construction.duoduo.supply;

import com.mojang.logging.LogUtils;
import com.homestead.sellveg.api.MallPurchaseApi;
import com.touhoumaid.construction.api.ConstructionSupplyApi;
import com.touhoumaid.construction.api.PurchaseIngredientLine;
import com.touhoumaid.construction.api.ShortageLine;
import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import com.touhoumaid.construction.item.ConstructionTerminalItem;
import com.touhoumaid.construction.item.ItemTerminalData;
import com.touhoumaid.construction.project.BuildProject;
import com.touhoumaid.construction.project.MaterialBlockPolicy;
import com.touhoumaid.construction.project.MaterialDisplay;
import com.touhoumaid.construction.project.SchematicFiles;
import com.touhoumaid.construction.project.VaultCounts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Resolves terminal + project and builds mall shortage rows for the companion UI. */
public final class SupplyListService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final java.util.Set<String> LOGGED_EXPAND_FAIL = new java.util.HashSet<>();

    private SupplyListService() {
    }

    public record TerminalSession(
            boolean itemMode,
            BlockPos pos,
            InteractionHand hand,
            BuildProject project,
            Container vault,
            Runnable saveVault) {
    }

    /**
     * @param targetItemId   finished shortage item (left-side name/icon)
     * @param purchaseItemId item actually bought (成品 or 原料)
     * @param ingredientBuy  true when this row buys craft ingredients for an out-of-stock finished item
     */
    public record Row(
            String blockId,
            String targetItemId,
            String purchaseItemId,
            int need,
            int unitPrice,
            boolean purchasable,
            boolean ingredientBuy) {
        public Row(String blockId, String purchaseItemId, int need, int unitPrice, boolean purchasable) {
            this(blockId, purchaseItemId, purchaseItemId, need, unitPrice, purchasable, false);
        }
    }

    public static Optional<TerminalSession> resolve(
            ServerPlayer player, boolean itemMode, BlockPos pos, InteractionHand hand, String projectId) {
        if (itemMode) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof ConstructionTerminalItem)) {
                return Optional.empty();
            }
            String resolvedId = projectId;
            if (resolvedId == null || resolvedId.isEmpty()) {
                resolvedId = ItemTerminalData.getSelectedId(stack);
            }
            Optional<BuildProject> project = BuildProject.find(ItemTerminalData.getProjects(stack), resolvedId);
            if (project.isEmpty()) {
                return Optional.empty();
            }
            // Portable vault lives in world SavedData; null server skips store (no-op save).
            var server = player.getServer();
            var items = ItemTerminalData.loadVaultItems(stack, server);
            Container vault = new net.minecraft.world.SimpleContainer(items.size());
            for (int i = 0; i < items.size(); i++) {
                vault.setItem(i, items.get(i));
            }
            return Optional.of(new TerminalSession(true, BlockPos.ZERO, hand, project.get(), vault, () -> {
                for (int i = 0; i < items.size(); i++) {
                    items.set(i, vault.getItem(i));
                }
                ItemTerminalData.saveVaultItems(stack, items, server);
            }));
        }
        BlockEntity be = player.level().getBlockEntity(pos);
        if (!(be instanceof ConstructionTerminalBE terminal)) {
            return Optional.empty();
        }
        Optional<BuildProject> project = BuildProject.find(terminal.getProjects(), projectId);
        if (project.isEmpty()) {
            project = terminal.getSelectedProject();
        }
        if (project.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TerminalSession(false, pos, hand, project.get(), terminal, terminal::setChanged));
    }

    public static List<Row> buildRows(ServerPlayer player, TerminalSession session) {
        List<Row> rows = new ArrayList<>();
        BuildProject project = session.project();
        if (project.schematicPath() == null || project.schematicPath().isEmpty()) {
            return rows;
        }
        try {
            Path path = SchematicFiles.resolveSchematic(project.schematicPath());
            SchematicFiles.MaterialSummary summary = SchematicFiles.summarize(path, project.startLayer());
            Map<String, Integer> vaultCounts = VaultCounts.fromContainer(session.vault());
            ServerLevel level = player.serverLevel();
            // Only virtually craft toward this project's purchase identities (avoids burning bone meal
            // into unrelated single-ingredient results before bone blocks).
            Set<String> purchaseTargets = new HashSet<>();
            for (SchematicFiles.MaterialLine line : summary.lines()) {
                if (line == null
                        || project.isIgnoredBlock(line.blockId())
                        || MaterialBlockPolicy.isAutoFree(line.blockId())) {
                    continue;
                }
                String purchaseId = MaterialDisplay.vaultItemIdForBlockId(line.blockId());
                if (purchaseId != null && !purchaseId.isEmpty()) {
                    purchaseTargets.add(purchaseId);
                }
            }
            Map<String, Integer> purchaseStock =
                    ConstructionSupplyApi.augmentPurchaseVaultStock(level, vaultCounts, purchaseTargets);
            List<ShortageLine> shortages =
                    ConstructionSupplyApi.listShortages(project, summary.lines(), purchaseStock);
            Map<String, Boolean> purchasableCache = new HashMap<>();
            for (ShortageLine line : shortages) {
                Item finished = resolveItem(line.purchaseItemId());
                MallPurchaseApi.Quote finishedQuote = finished == Items.AIR
                        ? MallPurchaseApi.Quote.unavailable()
                        : MallPurchaseApi.quote(player, finished);
                boolean finishedOk = finishedQuote.available() && finished != Items.AIR;
                if (finishedOk) {
                    rows.add(new Row(
                            line.blockId(),
                            line.purchaseItemId(),
                            line.purchaseItemId(),
                            line.need(),
                            finishedQuote.unitPrice(),
                            true,
                            false));
                    continue;
                }
                // Planner uses real vault counts so virtual hops cannot erase leaf stock.
                List<Row> ingredientRows = expandIngredientRows(player, level, line, vaultCounts, purchasableCache);
                if (ingredientRows == null) {
                    rows.add(new Row(
                            line.blockId(),
                            line.purchaseItemId(),
                            line.purchaseItemId(),
                            line.need(),
                            0,
                            false,
                            false));
                } else if (!ingredientRows.isEmpty()) {
                    rows.addAll(ingredientRows);
                }
                // empty list: vault already covers craft — omit the shortage row
            }
        } catch (Exception ignored) {
            return rows;
        }
        return rows;
    }

    /**
     * @return ingredient buy rows; empty list if vault already covers (omit row);
     *         null if expansion is impossible / incomplete (show finished as 无货)
     */
    private static List<Row> expandIngredientRows(
            ServerPlayer player,
            ServerLevel level,
            ShortageLine line,
            Map<String, Integer> vaultCounts,
            Map<String, Boolean> purchasableCache) {
        Optional<List<PurchaseIngredientLine>> planned = ConstructionSupplyApi.listPurchaseIngredients(
                level,
                line.purchaseItemId(),
                line.need(),
                vaultCounts,
                itemId -> isMallPurchasable(player, itemId, purchasableCache));
        if (planned.isEmpty()) {
            if (LOGGED_EXPAND_FAIL.add(line.purchaseItemId())) {
                LOGGER.info(
                        "[DuoduoSupply] ingredient expand miss target={} need={} planAbsent=true",
                        line.purchaseItemId(),
                        line.need());
            }
            return null;
        }
        if (planned.get().isEmpty()) {
            // API: empty list = vault covers craft, nothing to buy
            return List.of();
        }
        List<Row> out = new ArrayList<>(planned.get().size());
        for (PurchaseIngredientLine leaf : planned.get()) {
            Item item = resolveItem(leaf.itemId());
            MallPurchaseApi.Quote quote = item == Items.AIR
                    ? MallPurchaseApi.Quote.unavailable()
                    : MallPurchaseApi.quote(player, item);
            if (!quote.available() || item == Items.AIR || leaf.need() <= 0) {
                if (LOGGED_EXPAND_FAIL.add(line.purchaseItemId() + "->" + leaf.itemId())) {
                    LOGGER.info(
                            "[DuoduoSupply] ingredient expand leaf not buyable target={} leaf={} need={}",
                            line.purchaseItemId(),
                            leaf.itemId(),
                            leaf.need());
                }
                return null;
            }
            out.add(new Row(
                    line.blockId(),
                    line.purchaseItemId(),
                    leaf.itemId(),
                    leaf.need(),
                    quote.unitPrice(),
                    true,
                    true));
        }
        return out;
    }

    private static boolean isMallPurchasable(
            ServerPlayer player, String itemId, Map<String, Boolean> cache) {
        Boolean cached = cache.get(itemId);
        if (cached != null) {
            return cached;
        }
        Item item = resolveItem(itemId);
        boolean ok = item != Items.AIR && MallPurchaseApi.quote(player, item).available();
        cache.put(itemId, ok);
        return ok;
    }

    public static Item resolveItem(String itemId) {
        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null || !BuiltInRegistries.ITEM.containsKey(loc)) {
            return Items.AIR;
        }
        return BuiltInRegistries.ITEM.get(loc);
    }
}
