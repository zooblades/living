package dev.livinginv;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** A flat button in the style of the mod menu (works as a normal ButtonWidget: keyboard, narration, sound). */
public class FlatButton extends ButtonWidget {
    private static final int ACCENT = 0xFF4FD1C5;
    private final boolean primary;

    public FlatButton(int x, int y, int w, int h, Text label, PressAction onPress, boolean primary) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.primary = primary;
    }

    @Override
    public void renderButton(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean hot = active && (isHovered() || isFocused());
        int bg, border, text;
        if (!active) {
            bg = 0xFF141A21; border = 0xFF222C37; text = 0xFF5E6B78;
        } else if (primary) {
            bg = hot ? 0xFF2E8C84 : 0xFF22706A; border = ACCENT; text = 0xFFFFFFFF;
        } else {
            bg = hot ? 0xFF2A3A49 : 0xFF1C2630; border = hot ? ACCENT : 0xFF2C3947; text = 0xFFE8EEF4;
        }
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        ctx.fill(x, y, x + w, y + h, border);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        if (active) ctx.fill(x + 1, y + 1, x + w - 1, y + 2, 0x16FFFFFF);
        MinecraftClient mc = MinecraftClient.getInstance();
        ctx.drawCenteredTextWithShadow(mc.textRenderer, getMessage(), x + w / 2, y + (h - 8) / 2, text);
    }
}
