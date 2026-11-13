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

    public static final int CARD_WIDTH = 175;
    public static final int CARD_HEIGHT_WITH_MONEY = 52;
    public static final int CARD_HEIGHT_NO_MONEY = 40;

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
                // If sign is broken or destroyed in-world, immediately remove from HUD frame and config
                net.minecraft.world.level.block.state.BlockState state = client.level.getBlockState(pos);
                if (state.isAir() || !(state.getBlock() instanceof net.minecraft.world.level.block.SignBlock)) {
                    counters.remove(pos);
                    config.removeSignConfig(pos.getX(), pos.getY(), pos.getZ());
                    continue;
                }

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

        Font font = client.font;

        // Render a card for each active, enabled sign board in the sidebar
        for (CounterSign sign : enabledSigns) {
            SignConfigData sc = config.getOrCreateSignConfig(sign.getPos().getX(), sign.getPos().getY(), sign.getPos().getZ());
            boolean showMoneyThisSign = sc.showMoneyInHud && config.showMoneyInHud;
            int cardHeight = showMoneyThisSign ? CARD_HEIGHT_WITH_MONEY : CARD_HEIGHT_NO_MONEY;

            if (currentY + cardHeight > graphics.guiHeight() - 2) {
                break; // Don't overflow bottom of screen
            }

            // Background & Outline
            graphics.fill(hudX, currentY, hudX + CARD_WIDTH, currentY + cardHeight, 0x88000000);
            graphics.outline(hudX, currentY, CARD_WIDTH, cardHeight, 0x88555555);

            // 1. Sign Board Name & Single Mode Badge
            String signName = sign.getSignCustomName();
            String headerTitle;
            if (sign.getTargetDurationSeconds() <= 0) {
                headerTitle = "§6§l⚡ " + truncate(signName, 16) + " §a● [LIVE]";
            } else if (sign.isFinished()) {
                headerTitle = "§6§l⚡ " + truncate(signName, 16) + " §6★ [DONE - " + sign.getDisplayName() + "]";
            } else {
                headerTitle = "§6§l⚡ " + truncate(signName, 16) + " §e⏳ [" + sign.getDisplayName() + "]";
            }

            // 2. Entity / Items Passed (starts at 0, climbs up)
            int total = sign.getTotalItemsCounted();
            String rateText = (total == 0) ? "§7(Waiting...)" : ("§7(~" + String.format("%.0f", sign.getRatePerMinute()) + "/m)");
            String passedStr = "§fEntity Passed: §e§l" + total + " " + rateText;

            graphics.text(font, Component.literal(headerTitle), hudX + 5, currentY + 4, 0xFFFFFF, true);
            graphics.text(font, Component.literal(passedStr), hudX + 5, currentY + 15, 0xFFFFFF, true);

            // 3. Money Made in There
            int textY = currentY + 26;
            if (showMoneyThisSign) {
                double totalMoney = sign.getTotalMoneyValue();
                double moneyRateMin = sign.getMoneyRatePerMinute();
                String moneyStr = "§aMoney Made: §e§l" + config.currencySymbol + String.format("%,.0f", totalMoney) + " §7(§a~" + config.currencySymbol + String.format("%,.0f", moneyRateMin) + "/m§7)";
                graphics.text(font, Component.literal(moneyStr), hudX + 5, textY, 0xFFFFFF, true);
                textY += 11;
            }

            // 4. Status / Live Rate Monitor
            String timerStr;
            if (sign.getTargetDurationSeconds() <= 0) {
                timerStr = "§7Continuous Flow Monitor";
            } else if (sign.isFinished()) {
                timerStr = "§6★ Test Finished";
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
