package dev.livinginv;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Thin layer over APIs that differ between Minecraft versions. */
public final class Compat {
    private Compat() { }

    public static Text literal(String s) { return Text.literal(s); }

    public static Text translatable(String key, Object... args) { return Text.translatable(key, args); }

    public static ButtonWidget button(int x, int y, int w, int h, Text label, Runnable onPress) {
        return ButtonWidget.builder(label, b -> onPress.run()).dimensions(x, y, w, h).build();
    }

    public static void show(MinecraftClient client, Screen screen) { client.setScreen(screen); }

    public static InputStream openResource(MinecraftClient client, Identifier id) throws Exception {
        return client.getResourceManager().getResource(id).orElseThrow(() -> new IllegalStateException("overlay texture missing")).getInputStream();
    }
}
