package com.touhoumaid.construction.duoduo.client;

import com.homestead.sellveg.currency.CurrencyHelper;
import com.touhoumaid.construction.duoduo.network.ConfirmSupplyPurchasePacket;
import com.touhoumaid.construction.duoduo.network.ModNetwork;
import com.touhoumaid.construction.duoduo.network.SupplyListSyncPacket;
import com.touhoumaid.construction.duoduo.supply.SupplyListService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class SupplyListScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 220;
    private static final int ROW_H = 18;
    private static final int VISIBLE_ROWS = 8;

    private final boolean itemMode;
    private final net.minecraft.core.BlockPos pos;
    private final net.minecraft.world.InteractionHand hand;
    private final String projectId;
    private final long coins;
    private final List<SupplyListService.Row> rows;
    private final Set<Integer> selected = new HashSet<>();

    private int left;
    private int top;
    private int scroll;
    private Button confirmBtn;

    public SupplyListScreen(SupplyListSyncPacket packet) {
        super(Component.translatable("gui.construction_duoduo_supply.title"));
        this.itemMode = packet.itemMode();
        this.pos = packet.pos();
        this.hand = packet.hand();
        this.projectId = packet.projectId();
        this.coins = packet.coins();
        this.rows = new ArrayList<>(packet.rows());
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).purchasable()) {
                selected.add(i);
            }
        }
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        clearWidgets();
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.construction_duoduo_supply.select_all"),
                        b -> selectAllPurchasable())
                .bounds(left + 8, top + PANEL_H - 28, 96, 20)
                .build());
        confirmBtn = addRenderableWidget(Button.builder(
                        Component.translatable("gui.construction_duoduo_supply.confirm"),
                        b -> confirm())
                .bounds(left + 112, top + PANEL_H - 28, 96, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.construction_duoduo_supply.cancel"),
                        b -> onClose())
                .bounds(left + 216, top + PANEL_H - 28, 96, 20)
                .build());
        updateConfirmActive();
    }

    private void selectAllPurchasable() {
        selected.clear();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).purchasable()) {
                selected.add(i);
            }
        }
        updateConfirmActive();
    }

    private void updateConfirmActive() {
        if (confirmBtn != null) {
            confirmBtn.active = !selected.isEmpty();
        }
    }

    private void confirm() {
        if (selected.isEmpty()) {
            return;
        }
        List<Integer> indices = new ArrayList<>(selected);
        indices.sort(Integer::compareTo);
        ModNetwork.CHANNEL.sendToServer(new ConfirmSupplyPurchasePacket(
                itemMode, pos, hand, projectId, indices));
    }

    private long selectedTotal() {
        long total = 0;
        for (int i : selected) {
            if (i < 0 || i >= rows.size()) {
                continue;
            }
            SupplyListService.Row row = rows.get(i);
            if (row.purchasable()) {
                total += (long) row.unitPrice() * row.need();
            }
        }
        return total;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF0E8D5B8);
        g.fill(left, top, left + PANEL_W, top + 22, 0xF0C4783A);
        g.drawString(font, title, left + 8, top + 7, 0xFFFFFFF0, false);

        g.drawString(font, Component.translatable(
                "gui.construction_duoduo_supply.balance", CurrencyHelper.formatCount(coins)),
                left + 8, top + 28, 0xFF3B2A1E, false);
        g.drawString(font, Component.translatable(
                "gui.construction_duoduo_supply.selected_total", CurrencyHelper.formatCount(selectedTotal())),
                left + 160, top + 28, 0xFF3B2A1E, false);

        int listTop = top + 44;
        int listBottom = top + PANEL_H - 36;
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("gui.construction_duoduo_supply.empty"),
                    left + 8, listTop + 8, 0xFF7A5C45, false);
        } else {
            int maxScroll = Math.max(0, rows.size() - VISIBLE_ROWS);
            scroll = Mth.clamp(scroll, 0, maxScroll);
            for (int vis = 0; vis < VISIBLE_ROWS; vis++) {
                int idx = scroll + vis;
                if (idx >= rows.size()) {
                    break;
                }
                int y = listTop + vis * ROW_H;
                renderRow(g, idx, left + 8, y, mouseX, mouseY);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderRow(GuiGraphics g, int idx, int x, int y, int mouseX, int mouseY) {
        SupplyListService.Row row = rows.get(idx);
        boolean can = row.purchasable();
        boolean on = selected.contains(idx);
        int boxColor = can ? (on ? 0xFF3D8B5A : 0xFF7A5C45) : 0xFF888888;
        g.fill(x, y + 3, x + 10, y + 13, boxColor);
        if (on && can) {
            g.drawString(font, "✓", x + 1, y + 3, 0xFFFFFFF0, false);
        }

        ItemStack targetIcon = iconFor(row.targetItemId());
        g.renderItem(targetIcon, x + 14, y);
        String targetName = targetIcon.isEmpty()
                ? row.targetItemId()
                : targetIcon.getHoverName().getString();
        int nameMax = row.ingredientBuy() ? 10 : 18;
        if (targetName.length() > nameMax) {
            targetName = targetName.substring(0, nameMax - 1) + "…";
        }
        g.drawString(font, targetName, x + 34, y + 5, can ? 0xFF3B2A1E : 0xFF888888, false);

        int needX = 148;
        int priceX = 196;
        if (row.ingredientBuy()) {
            ItemStack ingIcon = iconFor(row.purchaseItemId());
            g.renderItem(ingIcon, x + 118, y);
            String ingName = ingIcon.isEmpty()
                    ? row.purchaseItemId()
                    : ingIcon.getHoverName().getString();
            if (ingName.length() > 8) {
                ingName = ingName.substring(0, 7) + "…";
            }
            g.drawString(font, ingName, x + 138, y + 5, 0xFF3B2A1E, false);
            needX = 200;
            priceX = 240;
        }

        g.drawString(font, Component.translatable("gui.construction_duoduo_supply.need", row.need()),
                x + needX, y + 5, 0xFF3B2A1E, false);
        if (can) {
            long sub = (long) row.unitPrice() * row.need();
            g.drawString(font, Component.translatable(
                            "gui.construction_duoduo_supply.price_line", row.unitPrice(), Long.toString(sub)),
                    x + priceX, y + 5, 0xFF3B2A1E, false);
        } else {
            g.drawString(font, Component.translatable("gui.construction_duoduo_supply.out_of_stock"),
                    x + priceX, y + 5, 0xFFB54A3A, false);
        }
    }

    private static ItemStack iconFor(String itemId) {
        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null || !BuiltInRegistries.ITEM.containsKey(loc)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(loc);
        return item == Items.AIR ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !rows.isEmpty()) {
            int listTop = top + 44;
            int rel = (int) mouseY - listTop;
            if (rel >= 0 && mouseX >= left + 8 && mouseX <= left + PANEL_W - 8) {
                int vis = rel / ROW_H;
                if (vis >= 0 && vis < VISIBLE_ROWS) {
                    int idx = scroll + vis;
                    if (idx < rows.size() && rows.get(idx).purchasable()) {
                        if (selected.contains(idx)) {
                            selected.remove(idx);
                        } else {
                            selected.add(idx);
                        }
                        updateConfirmActive();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!rows.isEmpty()) {
            int maxScroll = Math.max(0, rows.size() - VISIBLE_ROWS);
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
