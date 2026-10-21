package dev.livinginv;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.Optional;

/**
 * Draws the inventory panel: the animation underneath, the semi-transparent overlay
 * (assets/livinginv/textures/gui/inventory_overlay.png, replaceable by resource packs) on top.
 * All version-independent logic lives in PanelCore.
 */
public final class Background {
    private static final Identifier TEX_ID = Identifier.tryParse("livinginv:background");
    private static final Identifier OVERLAY = Identifier.tryParse("livinginv:textures/gui/inventory_overlay.png");
    private static NativeImageBackedTexture texture;

    private Background() { }

    private static void destroyTexture() {
        if (texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(TEX_ID);
            texture = null;
        }
    }

    private static PanelCore.Overlay loadOverlay() throws Exception {
        try (InputStream in = Overlays.openWithFallback(LivingInvClient.config.overlay); NativeImage img = NativeImage.read(in)) {
            int k = Math.max(1, img.getWidth() / 256); // overlay is authored at 256*k px
            int w = PanelComposer.PANEL_W * k, h = PanelComposer.PANEL_H * k;
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    px[y * w + x] = PanelCore.swapRb(img.getColorArgb(x, y));
            return new PanelCore.Overlay(px, w, h);
        }
    }

    /** Returns false if nothing is loaded yet (the vanilla panel is drawn meanwhile). */
    public static boolean renderPanel(DrawContext ctx, InventoryScreen screen, int x, int y, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (PanelCore.takeTextureReset()) destroyTexture();
        PanelCore.View v = PanelCore.view(mc.getWindow().getScaleFactor(), Background::loadOverlay);
        if (v == null) {
            if (PanelCore.progress >= 0) {
                ctx.drawTextWithShadow(mc.textRenderer, "Living Inventory: loading " + PanelCore.progress + "%", x, y - 10, 0xFFFFFFFF);
            }
            return false;
        }

        boolean fresh = false;
        if (v.newTexture || texture == null) {
            destroyTexture();
            texture = new NativeImageBackedTexture(() -> "livinginv_background", new NativeImage(v.width, v.height, false));
            texture.setFilter(true, false);
            mc.getTextureManager().registerTexture(TEX_ID, texture);
            fresh = true;
        }
        if (v.upload || fresh) {
            NativeImage img = texture.getImage();
            int ow = v.width, oh = v.height;
            for (int yy = 0; yy < oh; yy++)
                for (int xx = 0; xx < ow; xx++)
                    img.setColorArgb(xx, yy, PanelCore.swapRb(v.pixels[yy * ow + xx]));
            texture.upload();
        }
        ctx.drawTexture(RenderLayer::getGuiTextured, TEX_ID, x, y, 0f, 0f, w, h, v.width, v.height, v.width, v.height);
        return true;
    }
}
