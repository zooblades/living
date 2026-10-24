package dev.livinginv;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Drawing primitives for the menu on this (MatrixStack era) Minecraft version. */
final class PickerPlatform implements PickerUi.Platform {
    private static final class Prev {
        Identifier id;
        int regionW, regionH, texW, texH;
        boolean failed;
    }

    private final PickerScreen screen;
    private MatrixStack m;
    private final Map<String, Prev> previews = new HashMap<String, Prev>();

    PickerPlatform(PickerScreen screen) { this.screen = screen; }

    void begin(MatrixStack stack) { m = stack; }

    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    @Override public void fill(int x1, int y1, int x2, int y2, int argb) { DrawableHelper.fill(m, x1, y1, x2, y2, argb); }

    @Override public void text(String s, int x, int y, int argb) { mc().textRenderer.drawWithShadow(m, s, (float) x, (float) y, argb); }

    @Override public int textWidth(String s) { return mc().textRenderer.getWidth(s); }

    @Override public String tr(String key) { return Compat.translatable(key).getString(); }

    @Override public String clipboard() { return mc().keyboard.getClipboard(); }

    @Override public void newLayer() { /* immediate mode: later draws are always on top */ }

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
                p.id = new Identifier("livinginv", "preview/" + Integer.toHexString(id.hashCode()));
                NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
                mc().getTextureManager().registerTexture(p.id, tex);
            } catch (Exception e) {
                p.failed = true;
            }
        }
        if (p.failed || p.id == null) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alphaPct / 100f);
        RenderSystem.setShaderTexture(0, p.id);
        DrawableHelper.drawTexture(m, x, y, w, h, 0f, 0f, p.regionW, p.regionH, p.texW, p.texH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
