package dev.livinginv;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

/** Thin layer over APIs that differ between Minecraft versions. */
public final class Compat {
    private Compat() { }

    public static Text literal(String s) { return new LiteralText(s); }

    public static Text translatable(String key, Object... args) { return new TranslatableText(key, args); }

    public static ButtonWidget button(int x, int y, int w, int h, Text label, Runnable onPress) {
        return new ButtonWidget(x, y, w, h, label, b -> onPress.run());
    }

    public static void show(MinecraftClient client, Screen screen) { client.openScreen(screen); }
}
