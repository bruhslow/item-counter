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

        BlockPos playerPos = client.player.blockPosition();
        Map<BlockPos, CounterSign> counters = CounterManager.getActiveCounters();

        // Collect all active counters that have showInHud == true within 192 blocks
        List<CounterSign> enabledSigns = new ArrayList<>();

        // 1. Check all in-world active counter signs
        for (Map.Entry<BlockPos, CounterSign> entry : counters.entrySet()) {
            BlockPos pos = entry.getKey();
            CounterSign sign = entry.getValue();

            // Skip if chunk is loaded and block is air or not a sign
            if (client.level.isLoaded(pos)) {
                net.minecraft.world.level.block.state.BlockState state = client.level.getBlockState(pos);
                if (state.isAir() || !(state.getBlock() instanceof net.minecraft.world.level.block.SignBlock)) {
                    continue;
                }
            }

            if (pos.distSqr(playerPos) <= 192 * 192) {
                SignConfigData sc = config.getOrCreateSignConfig(pos.getX(), pos.getY(), pos.getZ());
                if (sc.showInHud) {
                    enabledSigns.add(sign);
                }
            }
        }

        // 2. Also ensure any pre-configured signs for THIS SERVER are checked
        Map<String, SignConfigData> serverSigns = config.getSignsForCurrentServer();
        for (SignConfigData sc : serverSigns.values()) {
            if (!sc.showInHud) continue;
                BlockPos pos = new BlockPos(sc.x, sc.y, sc.z);
                if (counters.containsKey(pos)) continue; // Already handled above

                // Skip if chunk is loaded and block is air
                if (client.level.isLoaded(pos)) {
                    net.minecraft.world.level.block.state.BlockState state = client.level.getBlockState(pos);
                    if (state.isAir() || !(state.getBlock() instanceof net.minecraft.world.level.block.SignBlock)) {
                        continue;
                    }
                }

                if (pos.distSqr(playerPos) <= 192 * 192) {
                    CounterSign sign = counters.computeIfAbsent(pos, p -> {
                        CounterSign cs = new CounterSign(p);
                        cs.setCustomDuration(sc.testDurationSeconds);
                        return cs;
                    });
                    enabledSigns.add(sign);
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

            boolean showHeader = sc.showHudHeader;
            boolean showCount = sc.showHudCount;
            boolean showMoney = sc.showMoneyInHud && config.showMoneyInHud;
            boolean showFooter = sc.showHudFooter;

            int lineCount = 0;
            if (showHeader) lineCount++;
            if (showCount) lineCount++;
            if (showMoney) lineCount++;
            if (showFooter) lineCount++;

            if (lineCount == 0) continue; // Nothing to render for this card

            int cardHeight = 8 + (lineCount * 11);

            if (currentY + cardHeight > graphics.guiHeight() - 2) {
                break; // Don't overflow bottom of screen
            }

            // Sleek Modern Card Background & Outline
            graphics.fill(hudX, currentY, hudX + CARD_WIDTH, currentY + cardHeight, 0xC0101520);
            graphics.outline(hudX, currentY, CARD_WIDTH, cardHeight, 0xFF354555);

            int textY = currentY + 4;

            // 1. Sign Board Name & Single Mode Badge
            if (showHeader) {
                String rawName = sign.getSignCustomName();
                String cleanName = rawName.replaceAll("\\[(Live Rate|10s|30s|60s|LIVE RATE)\\]", "").trim();
                String headerTitle = "§6§l⚡ " + truncate(cleanName, 16);
                if (sc.showHudBadge) {
                    if (sign.getTargetDurationSeconds() <= 0) {
                        headerTitle += " §a● [LIVE]";
                    } else if (sign.isFinished()) {
                        headerTitle += " §6★ [DONE]";
                    } else {
                        headerTitle += " §e⏳ [" + sign.getDisplayName() + "]";
                    }
                }
                graphics.text(font, Component.literal(headerTitle), hudX + 5, textY, 0xFFFFFF, true);
                textY += 11;
            }

            // 2. Entity / Items Passed (starts at 0, climbs up)
            if (showCount) {
                int total = sign.getTotalItemsCounted();
                String passedStr = "§fEntity Passed: §e§l" + total;
                if (sc.showHudRate) {
                    String rateText = (total == 0) ? " §7(Waiting...)" : (" §7(~" + String.format("%.0f", sign.getRatePerMinute()) + "/m)");
                    passedStr += rateText;
                }
                graphics.text(font, Component.literal(passedStr), hudX + 5, textY, 0xFFFFFF, true);
                textY += 11;
            }

            // 3. Money Made in There
            if (showMoney) {
                double totalMoney = sign.getTotalMoneyValue();
                String moneyStr = "§aMoney Made: §e§l" + config.currencySymbol + String.format("%,.0f", totalMoney);
                if (sc.showHudMoneyRate) {
                    double moneyRateMin = sign.getMoneyRatePerMinute();
                    moneyStr += " §7(§a~" + config.currencySymbol + String.format("%,.0f", moneyRateMin) + "/m§7)";
                }
                graphics.text(font, Component.literal(moneyStr), hudX + 5, textY, 0xFFFFFF, true);
                textY += 11;
            }

            // 4. Status / Live Rate Monitor
            if (showFooter) {
                String timerStr;
                if (sign.getTargetDurationSeconds() <= 0) {
                    timerStr = "§7Continuous Flow Monitor";
                } else if (sign.isFinished()) {
                    timerStr = "§6★ Test Finished";
                } else {
                    timerStr = "§a⏳ " + sign.getRemainingSeconds() + "s remaining";
                }
                graphics.text(font, Component.literal(timerStr), hudX + 5, textY, 0xFFFFFF, true);
                textY += 11;
            }

            currentY += cardHeight + 4;
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars - 2) + "..";
    }
}
