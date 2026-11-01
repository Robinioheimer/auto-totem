package de.robin.autototem;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

/** Visible client-side inventory helper. */
public final class AutoTotemClient implements ClientModInitializer {
    private static final int CURSOR_TRAVEL_TICKS = 12;
    private static final int OFFHAND_MENU_SLOT = 45;
    private static final int INVENTORY_GUI_WIDTH = 176;
    private static final int INVENTORY_GUI_HEIGHT = 166;

    private static KeyMapping activateKey;
    private static boolean active;
    private static int travelTick;
    private static Slot sourceSlot;
    private static Slot offhandSlot;
    private static int sourceSlotId;
    private static double startX, startY, targetX, targetY;
    private static Stage stage = Stage.IDLE;

    private enum Stage { IDLE, MOVE_TO_TOTEM, MOVE_TO_OFFHAND, RETURN_OLD_OFFHAND, FINISH }

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("autototem", "main"));
        activateKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autototem.activate", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category));
        ClientTickEvents.END_CLIENT_TICK.register(AutoTotemClient::tick);
    }

    private static void tick(Minecraft client) {
        while (activateKey.consumeClick()) {
            if (!active) begin(client);
        }
        if (!active) return;
        if (client.player == null || !(client.gui.screen() instanceof InventoryScreen screen)) {
            reset();
            return;
        }

        switch (stage) {
            case IDLE -> findTotem(client);
            case MOVE_TO_TOTEM -> moveAndClick(client, screen, sourceSlot, sourceSlotId, Stage.MOVE_TO_OFFHAND);
            case MOVE_TO_OFFHAND -> moveAndClick(client, screen, offhandSlot, OFFHAND_MENU_SLOT, Stage.RETURN_OLD_OFFHAND);
            case RETURN_OLD_OFFHAND -> {
                if (client.player.inventoryMenu.getCarried().isEmpty()) stage = Stage.FINISH;
                else moveAndClick(client, screen, sourceSlot, sourceSlotId, Stage.FINISH);
            }
            case FINISH -> {
                client.gui.setScreen(null);
                reset();
            }
        }
    }

    private static void begin(Minecraft client) {
        if (client.player == null || client.level == null || client.gui.screen() != null) return;
        active = true;
        stage = Stage.IDLE;
        client.gui.setScreen(new InventoryScreen(client.player));
        // Use Minecraft's own input handler to release the captured cursor.
        client.mouseHandler.releaseMouse();
    }

    private static void findTotem(Minecraft client) {
        var menu = client.player.inventoryMenu;
        if (client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            stage = Stage.FINISH;
            return;
        }
        sourceSlot = null;
        sourceSlotId = -1;
        for (int id = 0; id < menu.slots.size(); id++) {
            Slot slot = menu.slots.get(id);
            if (id != OFFHAND_MENU_SLOT && slot.hasItem() && slot.getItem().is(Items.TOTEM_OF_UNDYING)) {
                sourceSlot = slot;
                sourceSlotId = id;
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
        InventoryScreen screen = (InventoryScreen) client.gui.screen();
        Window window = client.getWindow();
        // Keep Minecraft's internal mouse state in sync with the visible OS cursor.
        client.mouseHandler.releaseMouse();
        GLFW.glfwSetInputMode(window.handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer mx = stack.mallocDouble(1), my = stack.mallocDouble(1);
            IntBuffer ww = stack.mallocInt(1), wh = stack.mallocInt(1);
            GLFW.glfwGetCursorPos(window.handle(), mx, my);
            GLFW.glfwGetWindowSize(window.handle(), ww, wh);
            startX = mx.get(0) * screen.width / Math.max(1.0, ww.get(0));
            startY = my.get(0) * screen.height / Math.max(1.0, wh.get(0));
        }
        double left = (screen.width - INVENTORY_GUI_WIDTH) / 2.0;
        double top = (screen.height - INVENTORY_GUI_HEIGHT) / 2.0;
        targetX = left + slot.x + 8.0;
        targetY = top + slot.y + 8.0;
        travelTick = 0;
        stage = nextStage;
    }

    private static void moveAndClick(Minecraft client, InventoryScreen screen, Slot slot,
                                     int menuSlotId, Stage nextStage) {
        if (slot == null) {
            stage = Stage.FINISH;
            return;
        }
        if (travelTick == 0) prepareMove(client, slot, stage);

        travelTick++;
        double p = Math.min(1.0, (double) travelTick / CURSOR_TRAVEL_TICKS);
        double guiX = startX + (targetX - startX) * p;
        double guiY = startY + (targetY - startY) * p;

        Window window = client.getWindow();
        // Minecraft may recapture the cursor after a screen transition; release it
        // through MouseHandler before positioning it for this animation frame.
        client.mouseHandler.releaseMouse();
        GLFW.glfwSetInputMode(window.handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ww = stack.mallocInt(1), wh = stack.mallocInt(1);
            GLFW.glfwGetWindowSize(window.handle(), ww, wh);
            double windowX = guiX * ww.get(0) / Math.max(1.0, screen.width);
            double windowY = guiY * wh.get(0) / Math.max(1.0, screen.height);
            GLFW.glfwSetCursorPos(window.handle(), windowX, windowY);
        }
        screen.mouseMoved(guiX, guiY);

        if (travelTick >= CURSOR_TRAVEL_TICKS) {
            client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId,
                    menuSlotId, 0, ContainerInput.PICKUP, client.player);
            travelTick = 0;
            stage = nextStage;
        }
    }

    private static void reset() {
        active = false;
        travelTick = 0;
        sourceSlot = null;
        offhandSlot = null;
        sourceSlotId = -1;
        stage = Stage.IDLE;
    }
}
