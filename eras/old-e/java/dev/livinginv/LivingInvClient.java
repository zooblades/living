package dev.livinginv;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

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
        ScreenEvents.AFTER_INIT.register((MinecraftClient client, Screen screen, int w, int h) -> {
            if (screen instanceof InventoryScreen) {
                Screens.getButtons(screen).add(Compat.button(4, 4, 28, 16, Compat.literal("GIF"),
                        () -> Compat.show(client, new PickerScreen(screen))));
            }
        });
    }

    public static void saveConfig() {
        config.save(configFile);
    }
}
