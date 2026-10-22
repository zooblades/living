package dev.livinginv;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
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
    public boolean mouseClicked(Click click, boolean doubled) {
        return ui.mouseClicked(click.x(), click.y(), click.button()) || super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        return ui.mouseDragged(click.x(), click.y()) || super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        return ui.mouseReleased() || super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return ui.mouseScrolled(x, y, vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean charTyped(CharInput input) {
        return ui.charTyped(input.codepoint()) || super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        return ui.keyPressed(input.key(), input.modifiers()) || super.keyPressed(input);
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
}
