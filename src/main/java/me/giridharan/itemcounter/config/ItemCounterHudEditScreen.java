package me.giridharan.itemcounter.config;

import me.giridharan.itemcounter.counter.CounterManager;
import me.giridharan.itemcounter.counter.CounterSign;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class ItemCounterHudEditScreen extends Screen {

    private final Screen parentScreen;
    private final ItemCounterConfig config;

    private int hudX;
    private int hudY;
    private static final int BOX_WIDTH = 150;
    private static final int BOX_HEIGHT = 48;

    private boolean isDragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public ItemCounterHudEditScreen(Screen parentScreen) {
        super(Component.literal("Edit Live Watch HUD Position"));
        this.parentScreen = parentScreen;
        this.config = ItemCounterConfig.getInstance();
        this.hudX = config.liveWatchX;
        this.hudY = config.liveWatchY;
    }

    @Override
    protected void init() {
        super.init();

        // Clamp coordinates within current window size
        clampCoordinates();

        // Save & Exit button
        this.addRenderableWidget(Button.builder(Component.literal("§a✔ Save Position"), button -> {
            savePosition();
            this.minecraft.gui.setScreen(parentScreen);
        }).bounds(this.width / 2 - 105, this.height - 30, 100, 20).build());

        // Reset to Default button
        this.addRenderableWidget(Button.builder(Component.literal("§c↺ Reset Default"), button -> {
            this.hudX = 10;
            this.hudY = 10;
            savePosition();
        }).bounds(this.width / 2 + 5, this.height - 30, 100, 20).build());
    }

    private void clampCoordinates() {
        hudX = Math.max(2, Math.min(this.width - BOX_WIDTH - 2, hudX));
        hudY = Math.max(2, Math.min(this.height - BOX_HEIGHT - 35, hudY));
    }

    private void savePosition() {
        config.liveWatchX = hudX;
        config.liveWatchY = hudY;
        config.save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Semi-transparent backdrop overlay
        graphics.fill(0, 0, this.width, this.height, 0x60000000);

        // Header instructions
        graphics.centeredText(this.font, Component.literal("§6§l⚡ Item Counter - Move Live Watch HUD"), this.width / 2, 12, 0xFFFFFF);
        graphics.centeredText(this.font, Component.literal("§7Click and drag the box to place it anywhere on screen. Click Save when finished."), this.width / 2, 24, 0xAAAAAA);

        // Render the HUD Box preview
        renderHudBox(graphics, mouseX, mouseY);

        // Render buttons
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderHudBox(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean hovered = mouseX >= hudX && mouseX <= hudX + BOX_WIDTH && mouseY >= hudY && mouseY <= hudY + BOX_HEIGHT;

        // Background
        int bgColor = isDragging ? 0xD0101520 : (hovered ? 0xB0151E28 : 0x90000000);
        graphics.fill(hudX, hudY, hudX + BOX_WIDTH, hudY + BOX_HEIGHT, bgColor);

        // Outline border
        int borderColor = isDragging ? 0xFF00FFAA : (hovered ? 0xFFFFAA00 : 0xFF555555);
        graphics.outline(hudX, hudY, BOX_WIDTH, BOX_HEIGHT, borderColor);

        // Get actual active counter or preview data
        CounterSign sampleCounter = getClosestActiveCounter();
        String headerTitle;
        String countStr;
        String speedStr;
        String timerStr;

        if (sampleCounter != null) {
            String modeName = sampleCounter.getDisplayName();
            String status = sampleCounter.isFinished() ? "§a[DONE]" : (sampleCounter.isRunning() ? "§e[LIVE]" : "§7[PAUSED]");
            headerTitle = "§6⚡ Counter §b[" + modeName + "] " + status;
            countStr = "§fItems: §e§l" + sampleCounter.getTotalItemsCounted();
            speedStr = "§bSpeed: §a~" + String.format("%.0f", sampleCounter.getRatePerMinute()) + "/m §7(" + String.format("%.1f", sampleCounter.getRatePerSecond()) + "/s)";
            if (sampleCounter.getTargetDurationSeconds() <= 0) {
                timerStr = "§a● Live Rate Monitor";
            } else if (sampleCounter.isFinished()) {
                timerStr = "§6★ Test Completed";
            } else {
                timerStr = "§a⏳ " + sampleCounter.getRemainingSeconds() + "s remaining";
            }
        } else {
            // Default Preview Data
            headerTitle = "§6⚡ Counter §b[30s] §e[LIVE]";
            countStr = "§fItems: §e§l142 items";
            speedStr = "§bSpeed: §a~284/m §7(4.7/s)";
            timerStr = "§a⏳ 15s remaining";
        }

        graphics.text(this.font, Component.literal(headerTitle), hudX + 5, hudY + 4, 0xFFFFFF, true);
        graphics.text(this.font, Component.literal(countStr), hudX + 5, hudY + 15, 0xFFFFFF, true);
        graphics.text(this.font, Component.literal(speedStr), hudX + 5, hudY + 25, 0xFFFFFF, true);
        graphics.text(this.font, Component.literal(timerStr), hudX + 5, hudY + 35, 0xFFFFFF, true);

        // Drag handle indicator
        String handleText = isDragging ? "§a[DRAGGING]" : "§e[✥ MOVE]";
        graphics.text(this.font, Component.literal(handleText), hudX + BOX_WIDTH - this.font.width(handleText) - 4, hudY + 4, 0xFFFFFF, false);
    }

    private CounterSign getClosestActiveCounter() {
        if (this.minecraft.player == null) return null;
        Map<BlockPos, CounterSign> counters = CounterManager.getActiveCounters();
        if (counters.isEmpty()) return null;

        BlockPos playerPos = this.minecraft.player.blockPosition();
        CounterSign closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Map.Entry<BlockPos, CounterSign> entry : counters.entrySet()) {
            double d = entry.getKey().distSqr(playerPos);
            if (d < closestDist) {
                closestDist = d;
                closest = entry.getValue();
            }
        }
        return closest;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasHandled) {
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            if (mx >= hudX && mx <= hudX + BOX_WIDTH && my >= hudY && my <= hudY + BOX_HEIGHT) {
                isDragging = true;
                dragOffsetX = (int) mx - hudX;
                dragOffsetY = (int) my - hudY;
                return true;
            }
        }
        return super.mouseClicked(event, wasHandled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && isDragging) {
            isDragging = false;
            clampCoordinates();
            savePosition();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (isDragging) {
            hudX = (int) event.x() - dragOffsetX;
            hudY = (int) event.y() - dragOffsetY;
            clampCoordinates();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public void onClose() {
        savePosition();
        this.minecraft.gui.setScreen(parentScreen);
    }
}
