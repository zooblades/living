package dev.livinginv;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Drawing primitives for the menu on this Minecraft version. */
final class PickerPlatform implements PickerUi.Platform {
    private static final class Prev {
        Identifier id;
        int regionW, regionH, texW, texH;
        boolean failed;
    }

    private final PickerScreen screen;
    private DrawContext ctx;
    private final Map<String, Prev> previews = new HashMap<String, Prev>();

    PickerPlatform(PickerScreen screen) { this.screen = screen; }

    void begin(DrawContext c) { ctx = c; }

    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    @Override public void fill(int x1, int y1, int x2, int y2, int argb) { ctx.fill(x1, y1, x2, y2, argb); }

    @Override public void text(String s, int x, int y, int argb) { ctx.drawTextWithShadow(mc().textRenderer, s, x, y, argb); }

    @Override public int textWidth(String s) { return mc().textRenderer.getWidth(s); }

    @Override public String tr(String key) { return Text.translatable(key).getString(); }

    @Override public String clipboard() { return mc().keyboard.getClipboard(); }

    @Override public void close() { screen.close(); }

    @Override
    public void releasePreviews() {
        for (Prev p : previews.values()) if (p.id != null) mc().getTextureManager().destroyTexture(p.id);
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
                p.id = Identifier.tryParse("livinginv:preview/" + Integer.toHexString(id.hashCode()));
                NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
                mc().getTextureManager().registerTexture(p.id, tex);
            } catch (Exception e) {
                p.failed = true;
            }
        }
        if (p.failed || p.id == null) return;
        int col = ((alphaPct * 255 / 100) << 24) | 0xFFFFFF;
        ctx.drawTexture(RenderLayer::getGuiTextured, p.id, x, y, 0f, 0f, w, h, p.regionW, p.regionH, p.texW, p.texH, col);
    }
}
