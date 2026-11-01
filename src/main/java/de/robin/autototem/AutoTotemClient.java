package de.robin.autototem;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

/**
 * A visible, client-side inventory helper.
 *
 * Press G to open the player inventory, move the visible cursor to the first
 * Totem of Undying, and place it in the offhand using normal menu click actions.
 */
public final class AutoTotemClient implements ClientModInitializer {
    private static final int CURSOR_TRAVEL_TICKS = 6;
    private static final int OFFHAND_MENU_SLOT = 45;
    private static final int INVENTORY_GUI_WIDTH = 176;
    private static final int INVENTORY_GUI_HEIGHT = 166;

    private static KeyMapping activateKey;
    private static boolean active;
    private static int travelTick;
    private static Slot sourceSlot;
    private static Slot offhandSlot;
    private static double startX;
    private static double startY;
    private static double targetX;
    private static double targetY;
    private static Stage stage = Stage.IDLE;

    private enum Stage {
        IDLE,
        MOVE_TO_TOTEM,
        MOVE_TO_OFFHAND,
        RETURN_OLD_OFFHAND,
        FINISH
    }

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                ResourceLocation.fromNamespaceAndPath("autototem", "main")
        );

        activateKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autototem.activate",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(AutoTotemClient::tick);
    }

    private static void tick(Minecraft client) {
        while (activateKey.consumeClick()) {
            if (!active) {
                begin(client);
            }
        }

        if (!active) {
            return;
        }

        if (client.player == null || !(client.screen instanceof InventoryScreen screen)) {
            reset();
            return;
        }

        switch (stage) {
            case IDLE -> findTotem(client);
            case MOVE_TO_TOTEM -> moveAndClick(client, screen, sourceSlot, Stage.MOVE_TO_OFFHAND);
            case MOVE_TO_OFFHAND -> moveAndClick(client, screen, offhandSlot, Stage.RETURN_OLD_OFFHAND);
            case RETURN_OLD_OFFHAND -> {
                // If the offhand was empty, the cursor is empty after the second click.
                // Otherwise, put the previously equipped item back into the source slot.
                if (client.player.inventoryMenu.getCarried().isEmpty()) {
                    stage = Stage.FINISH;
                } else {
                    moveAndClick(client, screen, sourceSlot, Stage.FINISH);
                }
            }
            case FINISH -> {
                client.gui.setScreen(null);
                reset();
            }
        }
    }

    private static void begin(Minecraft client) {
        if (client.player == null || client.level == null || client.screen != null) {
            return;
        }

        active = true;
        stage = Stage.IDLE;
        client.gui.setScreen(new InventoryScreen(client.player));
    }

    private static void findTotem(Minecraft client) {
        var menu = client.player.inventoryMenu;

        if (client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            stage = Stage.FINISH;
            return;
        }

        sourceSlot = null;
        for (Slot slot : menu.slots) {
            if (slot.index != OFFHAND_MENU_SLOT
                    && slot.hasItem()
                    && slot.getItem().is(Items.TOTEM_OF_UNDYING)) {
                sourceSlot = slot;
                break;
            }
        }

        if (sourceSlot == null) {
            stage = Stage.FINISH;
            return;
        }

        offhandSlot = menu.slots.get(OFFHAND_MENU_SLOT);
        prepareMove(client, sourceSlot, Stage.MOVE_TO_TOTEM);
    }

    private static void prepareMove(Minecraft client, Slot slot, Stage nextStage) {
        InventoryScreen screen = (InventoryScreen) client.screen;
        Window window = client.getWindow();
        double scale = window.getGuiScale();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer mouseX = stack.mallocDouble(1);
            DoubleBuffer mouseY = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window.getWindow(), mouseX, mouseY);
            startX = mouseX.get(0) / scale;
            startY = mouseY.get(0) / scale;
        }

        double left = (screen.width - INVENTORY_GUI_WIDTH) / 2.0;
        double top = (screen.height - INVENTORY_GUI_HEIGHT) / 2.0;
        targetX = left + slot.x + 8.0;
        targetY = top + slot.y + 8.0;
        travelTick = 0;
        stage = nextStage;
    }

    private static void moveAndClick(
            Minecraft client,
            InventoryScreen screen,
            Slot slot,
            Stage nextStage
    ) {
        if (slot == null) {
            stage = Stage.FINISH;
            return;
        }

        // Prepare the next leg when entering it.
        if (travelTick == 0) {
            prepareMove(client, slot, stage);
        }

        travelTick++;
        double progress = Math.min(1.0, (double) travelTick / CURSOR_TRAVEL_TICKS);
        double x = startX + (targetX - startX) * progress;
        double y = startY + (targetY - startY) * progress;

        Window window = client.getWindow();
        double scale = window.getGuiScale();
        GLFW.glfwSetCursorPos(window.getWindow(), x * scale, y * scale);
        screen.mouseMoved(x, y);

        if (travelTick >= CURSOR_TRAVEL_TICKS) {
            client.gameMode.handleInventoryMouseClick(
                    client.player.inventoryMenu.containerId,
                    slot.index,
                    0,
                    ClickType.PICKUP,
                    client.player
            );
            travelTick = 0;

            if (nextStage == Stage.MOVE_TO_OFFHAND) {
                stage = Stage.MOVE_TO_OFFHAND;
            } else if (nextStage == Stage.RETURN_OLD_OFFHAND) {
                stage = Stage.RETURN_OLD_OFFHAND;
            } else {
                stage = Stage.FINISH;
            }
        }
    }

    private static void reset() {
        active = false;
        travelTick = 0;
        sourceSlot = null;
        offhandSlot = null;
        stage = Stage.IDLE;
    }
}
