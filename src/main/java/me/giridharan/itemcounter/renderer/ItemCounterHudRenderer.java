package me.giridharan.itemcounter.renderer;

import me.giridharan.itemcounter.config.ItemCounterConfig;
import me.giridharan.itemcounter.config.ItemCounterHudEditScreen;
import me.giridharan.itemcounter.config.SignConfigData;
import me.giridharan.itemcounter.counter.CounterManager;
import me.giridharan.itemcounter.counter.CounterSign;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemCounterHudRenderer {

    public static final int CARD_WIDTH = 165;
    public static final int CARD_HEIGHT_WITH_MONEY = 56;
    public static final int CARD_HEIGHT_NO_MONEY = 46;

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("itemcounter", "live_watch"),
                ItemCounterHudRenderer::render
        );
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        ItemCounterConfig config = ItemCounterConfig.getInstance();
        if (!config.showLiveWatchHud) {
            return;
        }

        // Skip normal HUD rendering while user is dragging/editing the HUD box position
        if (client.gui.screen() instanceof ItemCounterHudEditScreen) {
            return;
        }

        Map<BlockPos, CounterSign> counters = CounterManager.getActiveCounters();
        if (counters.isEmpty()) {
            return;
        }

        BlockPos playerPos = client.player.blockPosition();

        // Collect all active counters that have showInHud == true within 64 blocks
        List<CounterSign> enabledSigns = new ArrayList<>();
        for (Map.Entry<BlockPos, CounterSign> entry : counters.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.distSqr(playerPos) <= 64 * 64) {
                SignConfigData sc = config.getOrCreateSignConfig(pos.getX(), pos.getY(), pos.getZ());
                if (sc.showInHud) {
                    enabledSigns.add(entry.getValue());
                }
            }
        }

        if (enabledSigns.isEmpty()) {
            return;
        }

        int hudX = Math.max(2, Math.min(graphics.guiWidth() - CARD_WIDTH - 2, config.liveWatchX));
        int currentY = Math.max(2, config.liveWatchY);
        int cardHeight = config.showMoneyInHud ? CARD_HEIGHT_WITH_MONEY : CARD_HEIGHT_NO_MONEY;

        Font font = client.font;

        // Render a card for each active, enabled sign board in the sidebar
        for (CounterSign sign : enabledSigns) {
            if (currentY + cardHeight > graphics.guiHeight() - 2) {
                break; // Don't overflow bottom of screen
            }

            // Background & Outline
            graphics.fill(hudX, currentY, hudX + CARD_WIDTH, currentY + cardHeight, 0x88000000);
            graphics.outline(hudX, currentY, CARD_WIDTH, cardHeight, 0x88555555);

            String signName = sign.getSignCustomName();
            String modeName = sign.getDisplayName();
            String status = sign.isFinished() ? "§a[DONE]" : (sign.isRunning() ? "§e[LIVE]" : "§7[PAUSED]");
            String headerTitle = "§6⚡ " + truncate(signName, 14) + " §b[" + modeName + "] " + status;
            String countStr = "§fItems: §e§l" + sign.getTotalItemsCounted() + " §7(~" + String.format("%.0f", sign.getRatePerMinute()) + "/m)";

            graphics.text(font, Component.literal(headerTitle), hudX + 5, currentY + 4, 0xFFFFFF, true);
            graphics.text(font, Component.literal(countStr), hudX + 5, currentY + 15, 0xFFFFFF, true);

            int textY = currentY + 25;
            if (config.showMoneyInHud) {
                double totalMoney = sign.getTotalMoneyValue();
                double moneyRateMin = sign.getMoneyRatePerMinute();
                String moneyStr = "§aMoney: §e§l" + config.currencySymbol + String.format("%,.0f", totalMoney) + " §7(§a~" + config.currencySymbol + String.format("%,.0f", moneyRateMin) + "/m§7)";
                graphics.text(font, Component.literal(moneyStr), hudX + 5, textY, 0xFFFFFF, true);
                textY += 10;
            }

            String timerStr;
            if (sign.getTargetDurationSeconds() <= 0) {
                timerStr = "§a● Live Rate Monitor";
            } else if (sign.isFinished()) {
                timerStr = "§6★ Test Complete";
            } else {
                timerStr = "§a⏳ " + sign.getRemainingSeconds() + "s remaining";
            }
            graphics.text(font, Component.literal(timerStr), hudX + 5, textY, 0xFFFFFF, true);

            currentY += cardHeight + 4;
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars - 2) + "..";
    }
}
