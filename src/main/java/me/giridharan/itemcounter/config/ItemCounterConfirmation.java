package me.giridharan.itemcounter.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ItemCounterConfirmation {

    public static void requestEnableCoordinates(Minecraft client, Screen returnScreen, Runnable onConfirmed) {
        Component title1 = Component.literal("§c§l⚠️ Privacy Warning: Show Coordinates? (Step 1/2)");
        Component msg1 = Component.literal(
                "§eEnabling coordinates will display exact world positions (X, Y, Z) in chat messages and lists.\n\n" +
                "§cWARNING: If you stream, record videos, or play on SMP servers, viewers or other players could see your farm coordinates!\n\n" +
                "§7Do you want to proceed to the final confirmation step?"
        );

        Component title2 = Component.literal("§4§l⚠️ FINAL CONFIRMATION: Show Coordinates? (Step 2/2)");
        Component msg2 = Component.literal(
                "§cAre you ABSOLUTELY sure you want to show coordinates in chat?\n\n" +
                "§eCoordinates will no longer be hidden in chat messages.\n\n" +
                "§7Click 'Confirm & Enable' to turn ON, or 'Cancel' to keep coordinates safe and hidden."
        );

        ConfirmScreen step2 = new ConfirmScreen(
                confirmed2 -> {
                    if (confirmed2) {
                        onConfirmed.run();
                        if (client.player != null) {
                            client.player.sendSystemMessage(Component.literal("§6§l[Item Counter] §eCoordinate display has been §c§lENABLED§e."));
                        }
                    }
                    client.execute(() -> client.gui.setScreen(returnScreen));
                },
                title2,
                msg2,
                Component.literal("§c§lConfirm & Enable"),
                Component.literal("§aCancel (Keep Hidden)")
        );

        ConfirmScreen step1 = new ConfirmScreen(
                confirmed1 -> {
                    if (confirmed1) {
                        client.execute(() -> client.gui.setScreen(step2));
                    } else {
                        client.execute(() -> client.gui.setScreen(returnScreen));
                    }
                },
                title1,
                msg1,
                Component.literal("§eYes, Proceed to Step 2"),
                Component.literal("§aCancel (Keep Hidden)")
        );

        client.execute(() -> client.gui.setScreen(step1));
    }
}

