package me.giridharan.itemcounter.renderer;

import me.giridharan.itemcounter.config.ItemCounterConfig;
import me.giridharan.itemcounter.config.ItemCounterHudEditScreen;
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

import java.util.Map;

public class ItemCounterHudRenderer {

    private static final int BOX_WIDTH = 150;
    private static final int BOX_HEIGHT = 48;

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
        CounterSign closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Map.Entry<BlockPos, CounterSign> entry : counters.entrySet()) {
            double d = entry.getKey().distSqr(playerPos);
            if (d < closestDist) {
                closestDist = d;
                closest = entry.getValue();
            }
        }

        // Only show if player is within 64 blocks of a counter
        if (closest == null || closestDist > 64 * 64) {
            return;
        }

        int hudX = Math.max(2, Math.min(graphics.guiWidth() - BOX_WIDTH - 2, config.liveWatchX));
        int hudY = Math.max(2, Math.min(graphics.guiHeight() - BOX_HEIGHT - 2, config.liveWatchY));

        // Semi-transparent dark background
        graphics.fill(hudX, hudY, hudX + BOX_WIDTH, hudY + BOX_HEIGHT, 0x85000000);
        // Subtle outline border
        graphics.outline(hudX, hudY, BOX_WIDTH, BOX_HEIGHT, 0x88444444);

        Font font = client.font;
        String modeName = closest.getDisplayName();
        String status = closest.isFinished() ? "§a[DONE]" : (closest.isRunning() ? "§e[LIVE]" : "§7[PAUSED]");
        String headerTitle = "§6⚡ Counter §b[" + modeName + "] " + status;
        String countStr = "§fItems: §e§l" + closest.getTotalItemsCounted();
        String speedStr = "§bSpeed: §a~" + String.format("%.0f", closest.getRatePerMinute()) + "/m §7(" + String.format("%.1f", closest.getRatePerSecond()) + "/s)";

        String timerStr;
        if (closest.getTargetDurationSeconds() <= 0) {
            timerStr = "§a● Live Rate Monitor";
        } else if (closest.isFinished()) {
            timerStr = "§6★ Test Completed";
        } else {
            timerStr = "§a⏳ " + closest.getRemainingSeconds() + "s remaining";
        }

        graphics.text(font, Component.literal(headerTitle), hudX + 5, hudY + 4, 0xFFFFFF, true);
        graphics.text(font, Component.literal(countStr), hudX + 5, hudY + 15, 0xFFFFFF, true);
        graphics.text(font, Component.literal(speedStr), hudX + 5, hudY + 25, 0xFFFFFF, true);
        graphics.text(font, Component.literal(timerStr), hudX + 5, hudY + 35, 0xFFFFFF, true);
    }
}
