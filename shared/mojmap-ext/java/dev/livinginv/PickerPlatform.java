package dev.livinginv;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Drawing primitives for the menu on 26.x. */
final class PickerPlatform implements PickerUi.Platform {
    private static final class Prev {
        Identifier id;
        int regionW, regionH, texW, texH;
        boolean failed;
    }

    private final PickerScreen screen;
    private GuiGraphicsExtractor g;
    private final Map<String, Prev> previews = new HashMap<String, Prev>();

    PickerPlatform(PickerScreen screen) { this.screen = screen; }

    void begin(GuiGraphicsExtractor graphics) { g = graphics; }

    private static Minecraft mc() { return Minecraft.getInstance(); }

    @Override public void fill(int x1, int y1, int x2, int y2, int argb) { g.fill(x1, y1, x2, y2, argb); }

    @Override public void text(String s, int x, int y, int argb) { g.text(mc().font, s, x, y, argb); }

    @Override public int textWidth(String s) { return mc().font.width(s); }

    @Override public String tr(String key) { return Component.translatable(key).getString(); }

    @Override public String clipboard() { return mc().keyboardHandler.getClipboard(); }

    @Override public void newLayer() { g.nextStratum(); }

    @Override public void close() { screen.onClose(); }

    @Override
    public void releasePreviews() {
        for (Prev p : previews.values()) if (p.id != null) mc().getTextureManager().release(p.id);
        previews.clear();
    }

    @Override
    public void drawPreview(String id, int x, int y, int w, int h, int alphaPct) {
        Prev p = previews.get(id);
        if (p == null) {
            p = new Prev();
            previews.put(id, p);
            try (InputStream in = Overlays.open(id)) {
                NativeImage img = NativeImage.read(in);
                int k = Math.max(1, img.getWidth() / 256);
                p.texW = img.getWidth();
                p.texH = img.getHeight();
                p.regionW = Math.min(p.texW, PanelComposer.PANEL_W * k);
                p.regionH = Math.min(p.texH, PanelComposer.PANEL_H * k);
                p.id = Identifier.fromNamespaceAndPath("livinginv", "preview/" + Integer.toHexString(id.hashCode()));
                mc().getTextureManager().register(p.id, new DynamicTexture(() -> "livinginv_preview", img));
            } catch (Exception e) {
                p.failed = true;
            }
        }
        if (p.failed || p.id == null) return;
        int col = ((alphaPct * 255 / 100) << 24) | 0xFFFFFF;
        g.blit(RenderPipelines.GUI_TEXTURED, p.id, x, y, 0f, 0f, w, h, p.regionW, p.regionH, p.texW, p.texH, col);
    }
}
