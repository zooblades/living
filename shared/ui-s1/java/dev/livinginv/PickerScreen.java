package dev.livinginv;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;

/** The mod menu screen: a thin shell, all logic lives in PickerUi. */
public class PickerScreen extends Screen {
    private final Screen parent;
    private final PickerPlatform platform = new PickerPlatform(this);
    private final PickerUi ui = new PickerUi(platform);

    public PickerScreen(Screen parent) {
        super(Text.translatable("livinginv.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ui.init(width, height);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta); // draws the (blurred) background the way this version expects
        platform.begin(ctx);
        ui.render(mx, my);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return ui.mouseClicked(x, y, button) || super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return ui.mouseDragged(x, y) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return ui.mouseReleased() || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return ui.mouseScrolled(x, y, vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return ui.charTyped(chr) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return ui.keyPressed(keyCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Screen declares one of these two names depending on the Minecraft version; the other is just a spare method.
    public void onFilesDropped(List<Path> paths) { ui.filesDropped(paths); }

    public void filesDragged(List<Path> paths) { ui.filesDropped(paths); }

    @Override
    public void removed() {
        ui.removed();
        super.removed();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    /** Test hooks. */
    public void debugTab(int t) { ui.debugTab(t); }

    public void debugSelect(int index) { ui.debugSelect(index); }

    public void debugRename(int index, String text) { ui.debugRename(index, text); }
}
