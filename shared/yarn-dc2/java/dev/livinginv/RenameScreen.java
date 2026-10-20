package dev.livinginv;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Small dialog that gives an animation a display name (the file itself is never renamed). */
public class RenameScreen extends Screen {
    private static final int BG = 0xF0121820, BORDER = 0xFF2C3947, ACCENT = 0xFF4FD1C5, TEXT = 0xFFE8EEF4, DIM = 0xFF8A98A6;
    private static final int ENTER = 257, KP_ENTER = 335;

    private final Screen parent;
    private final String file;
    private TextFieldWidget field;
    private int px, py, pw, ph;

    public RenameScreen(Screen parent, String file) {
        super(Text.translatable("livinginv.rename.title"));
        this.parent = parent;
        this.file = file;
    }

    private String baseName() {
        int dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 20, 270);
        ph = 118;
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        String current = LivingInvClient.config.names.get(file);
        field = new TextFieldWidget(textRenderer, px + 18, py + 50, pw - 36, 12, Text.translatable("livinginv.rename.title"));
        field.setDrawsBackground(false);
        field.setMaxLength(40);
        field.setPlaceholder(Text.literal(baseName()));
        field.setText(current == null ? "" : current);
        addDrawableChild(field);
        setInitialFocus(field);

        int bw = (pw - 20 - 6) / 2, by = py + ph - 28;
        addDrawableChild(new FlatButton(px + 10, by, bw, 20, Text.translatable("livinginv.cancel"), b -> close(), false));
        addDrawableChild(new FlatButton(px + 10 + bw + 6, by, bw, 20, Text.translatable("livinginv.ok"), b -> confirm(), true));
    }

    private void confirm() {
        String t = field.getText().trim();
        if (t.isEmpty()) LivingInvClient.config.names.remove(file);
        else LivingInvClient.config.names.put(file, t);
        LivingInvClient.saveConfig();
        close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == ENTER || keyCode == KP_ENTER) { confirm(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    /** All custom drawing happens here so that the field and buttons are rendered on top of it. */
    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        super.renderBackground(ctx, mx, my, delta);
        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, BORDER);
        ctx.fill(px, py, px + pw, py + ph, BG);
        ctx.fillGradient(px, py, px + pw, py + 30, 0xFF1F2A35, 0xFF161E26);
        ctx.fill(px, py + 30, px + pw, py + 31, BORDER);
        ctx.drawTextWithShadow(textRenderer, title, px + 12, py + 11, ACCENT);

        String shown = textRenderer.trimToWidth(file, pw - 24);
        ctx.drawTextWithShadow(textRenderer, shown, px + 12, py + 36, DIM);

        // input box
        int bx = px + 12, by = py + 46, bw = pw - 24, bh = 20;
        ctx.fill(bx, by, bx + bw, by + bh, field != null && field.isFocused() ? ACCENT : BORDER);
        ctx.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, 0xFF0E131A);

        ctx.drawTextWithShadow(textRenderer, Text.translatable("livinginv.rename.hint"), px + 12, py + 72, DIM);
    }
}
