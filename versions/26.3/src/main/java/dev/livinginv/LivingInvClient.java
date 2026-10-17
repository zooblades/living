package dev.livinginv;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LivingInvClient implements ClientModInitializer {
    public static Path dir;
    public static Config config;
    private static Path configFile;

    @Override
    public void onInitializeClient() {
        dir = FabricLoader.getInstance().getConfigDir().resolve("livinginv");
        try { Files.createDirectories(dir); } catch (IOException ignored) { }
        configFile = dir.resolve("config.json");
        config = Config.load(configFile);
        Background.reload();

        // Small "GIF" button in the corner of the survival inventory
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof InventoryScreen) {
                Screens.getWidgets(screen).add(Button.builder(Component.literal("GIF"),
                        b -> client.setScreen(new PickerScreen(screen))).bounds(4, 4, 28, 16).build());
            }
        });
    }

    public static void saveConfig() {
        config.save(configFile);
    }
}
