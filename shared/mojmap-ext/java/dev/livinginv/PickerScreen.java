package dev.livinginv;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

/** The mod menu screen: a thin shell, all logic lives in PickerUi. */
public class PickerScreen extends Screen {
    private final Screen parent;
    private final PickerPlatform platform = new PickerPlatform(this);
    private final PickerUi ui = new PickerUi(platform);

    public PickerScreen(Screen parent) {
        super(Component.translatable("livinginv.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ui.init(width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta); // draws the (blurred) background the way this version expects
        platform.begin(g);
        ui.render(mx, my);
    }

    /** 26.3 numbers mouse buttons the SDL way (left = 1); the menu core wants left = 0. */
    private static int primaryOrOther(int button) {
        return button == InputConstants.MOUSE_BUTTON_LEFT ? 0 : 1;
    }

    /** 26.3 reports SDL scancodes for keys; translate the ones the rename dialog needs to the core's GLFW-style codes. */
    private static int normalizeKey(int key) {
        if (key == InputConstants.KEY_RETURN) return 257;
        if (key == InputConstants.KEY_NUMPADENTER) return 335;
        if (key == InputConstants.KEY_ESCAPE) return 256;
        if (key == InputConstants.KEY_BACKSPACE) return 259;
        if (key == InputConstants.KEY_DELETE) return 261;
        if (key == InputConstants.KEY_LEFT) return 263;
        if (key == InputConstants.KEY_RIGHT) return 262;
        if (key == InputConstants.KEY_HOME) return 268;
        if (key == InputConstants.KEY_END) return 269;
        if (key == InputConstants.KEY_V) return 86;
        return key;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return ui.mouseClicked(event.x(), event.y(), primaryOrOther(event.button())) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return ui.mouseDragged(event.x(), event.y()) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return ui.mouseReleased() || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return ui.mouseScrolled(x, y, vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return ui.charTyped(event.codepoint()) || super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return ui.keyPressed(normalizeKey(event.key()), event.modifiers()) || super.keyPressed(event);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        ui.filesDropped(paths);
    }

    @Override
    public void removed() {
        ui.removed();
        super.removed();
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    /** Test hooks. */
    public void debugTab(int t) { ui.debugTab(t); }

    public void debugSelect(int index) { ui.debugSelect(index); }

    public void debugRename(int index, String text) { ui.debugRename(index, text); }
}
