package me.giridharan.itemcounter.config;

import me.giridharan.itemcounter.counter.CounterManager;
import me.giridharan.itemcounter.counter.CounterSign;
import me.giridharan.itemcounter.renderer.ItemCounterHudRenderer;
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

    private int getCardHeight() {
        return config.showMoneyInHud ? 52 : 40;
    }

    private void clampCoordinates() {
        int cardH = getCardHeight();
        hudX = Math.max(2, Math.min(this.width - ItemCounterHudRenderer.CARD_WIDTH - 2, hudX));
        hudY = Math.max(2, Math.min(this.height - cardH - 35, hudY));
    }

    private void savePosition() {
        config.showLiveWatchHud = true;
        config.liveWatchX = hudX;
        config.liveWatchY = hudY;
        config.save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Semi-transparent backdrop overlay
        graphics.fill(0, 0, this.width, this.height, 0x60000000);

        // Header instructions
        graphics.centeredText(this.font, Component.literal("§6§l⚡ Item Counter - Move Live Watch Sidebar HUD"), this.width / 2, 12, 0xFFFFFF);
        graphics.centeredText(this.font, Component.literal("§7Click and drag the box to place it anywhere on screen. Click Save when finished."), this.width / 2, 24, 0xAAAAAA);

        // Render the HUD Box preview
        renderHudBox(graphics, mouseX, mouseY);

        // Render buttons
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderHudBox(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int cardW = ItemCounterHudRenderer.CARD_WIDTH;
        int cardH = getCardHeight();

        boolean hovered = mouseX >= hudX && mouseX <= hudX + cardW && mouseY >= hudY && mouseY <= hudY + cardH;

        // Background
        int bgColor = isDragging ? 0xD0101520 : (hovered ? 0xB0151E28 : 0x90000000);
        graphics.fill(hudX, hudY, hudX + cardW, hudY + cardH, bgColor);

        // Outline border
        int borderColor = isDragging ? 0xFF00FFAA : (hovered ? 0xFFFFAA00 : 0xFF555555);
        graphics.outline(hudX, hudY, cardW, cardH, borderColor);

        // Get actual active counter or preview data
        CounterSign sampleCounter = getClosestActiveCounter();
        String signName;
        String modeName;
        String status;
        int count;
        double rateMin;
        double money;
        double moneyRate;
        String timerStr;

        if (sampleCounter != null) {
            signName = sampleCounter.getSignCustomName();
            modeName = sampleCounter.getDisplayName();
            status = sampleCounter.isFinished() ? "§6★ [DONE]" : (sampleCounter.getTargetDurationSeconds() <= 0 ? "§a● [LIVE]" : "§e⏳ [" + modeName + "]");
            count = sampleCounter.getTotalItemsCounted();
            rateMin = sampleCounter.getRatePerMinute();
            money = sampleCounter.getTotalMoneyValue();
            moneyRate = sampleCounter.getMoneyRatePerMinute();
            timerStr = sampleCounter.getTargetDurationSeconds() <= 0 ? "§7Continuous Flow Monitor" : "§a⏳ " + sampleCounter.getRemainingSeconds() + "s remaining";
        } else {
            signName = "Iron Farm (Sample)";
            modeName = "30s";
            status = "§e⏳ [30s]";
            count = 142;
            rateMin = 284;
            money = 1420;
            moneyRate = 2840;
            timerStr = "§a⏳ 15s remaining";
        }

        String headerTitle = "§6§l⚡ " + truncate(signName, 16) + " " + status;
        String passedStr = "§fEntity Passed: §e§l" + count + " items §7(~" + String.format("%.0f", rateMin) + "/m)";

        graphics.text(this.font, Component.literal(headerTitle), hudX + 5, hudY + 4, 0xFFFFFF, true);
        graphics.text(this.font, Component.literal(passedStr), hudX + 5, hudY + 15, 0xFFFFFF, true);

        int textY = hudY + 26;
        if (config.showMoneyInHud) {
            String moneyStr = "§aMoney Made: §e§l" + config.currencySymbol + String.format("%,.0f", money);
            graphics.text(this.font, Component.literal(moneyStr), hudX + 5, textY, 0xFFFFFF, true);
            textY += 11;
        }

        graphics.text(this.font, Component.literal(timerStr), hudX + 5, textY, 0xFFFFFF, true);

        // Drag handle indicator
        String handleText = isDragging ? "§a[DRAGGING]" : "§e[✥ MOVE]";
        graphics.text(this.font, Component.literal(handleText), hudX + cardW - this.font.width(handleText) - 4, hudY + 4, 0xFFFFFF, false);
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars - 2) + "..";
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
            int cardW = ItemCounterHudRenderer.CARD_WIDTH;
            int cardH = getCardHeight();

            if (mx >= hudX && mx <= hudX + cardW && my >= hudY && my <= hudY + cardH) {
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
