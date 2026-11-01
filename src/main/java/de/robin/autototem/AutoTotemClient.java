package de.robin.autototem;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;

/**
 * Initial project scaffold for the visible inventory-based Auto Totem.
 *
 * The hotkey and inventory scan are implemented here. The visible cursor
 * animation/click sequence is deliberately isolated as the next implementation
 * step because it depends on the exact 26.2 mouse callback mappings.
 */
public final class AutoTotemClient implements ClientModInitializer {
    private static KeyBinding activateKey;
    private static boolean waitingForInventory;

    @Override
    public void onInitializeClient() {
        activateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autototem.activate",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.autototem"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (activateKey.wasPressed()) {
                onHotkey(client);
            }

            if (waitingForInventory) {
                handleInventory(client);
            }
        });
    }

    private static void onHotkey(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }

        // Avoid interrupting containers, chat, pause menus, etc.
        if (client.currentScreen != null) {
            return;
        }

        waitingForInventory = true;
        client.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(client.player));
    }

    private static void handleInventory(MinecraftClient client) {
        if (client.player == null || client.currentScreen == null) {
            waitingForInventory = false;
            return;
        }

        if (!(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)) {
            waitingForInventory = false;
            return;
        }

        // Locate the first Totem of Undying in the player's inventory handler.
        // Slot coordinates are relative to the GUI and will be used by the
        // visible cursor movement implementation.
        for (Slot slot : client.player.playerScreenHandler.slots) {
            if (slot.hasStack() && slot.getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                // Found a candidate. The next stage will animate the real
                // cursor to slot.x/slot.y, perform the GUI click, then close.
                return;
            }
        }
    }
}
