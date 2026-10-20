package dev.livinginv;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.io.InputStream;

/**
 * Draws the inventory panel: the animation underneath, the semi-transparent overlay
 * (assets/livinginv/textures/gui/inventory_overlay.png, replaceable by resource packs) on top.
 * All version-independent logic lives in PanelCore.
 */
public final class Background {
    private static final Identifier TEX_ID = new Identifier("livinginv", "background");
    private static final Identifier OVERLAY = new Identifier("livinginv", "textures/gui/inventory_overlay.png");
    private static NativeImageBackedTexture texture;

    private Background() { }

    private static void destroyTexture() {
        if (texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(TEX_ID);
            texture = null;
        }
    }

    private static PanelCore.Overlay loadOverlay() throws Exception {
        MinecraftClient mc = MinecraftClient.getInstance();
        try (InputStream in = Compat.openResource(mc, OVERLAY); NativeImage img = NativeImage.read(in)) {
            int k = Math.max(1, img.getWidth() / 256); // overlay is authored at 256*k px
            int w = PanelComposer.PANEL_W * k, h = PanelComposer.PANEL_H * k;
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    px[y * w + x] = img.getColor(x, y); // ABGR
            return new PanelCore.Overlay(px, w, h);
        }
    }

    /** Returns false if nothing is loaded yet (the vanilla panel is drawn meanwhile). */
    public static boolean renderPanel(MatrixStack m, InventoryScreen screen, int x, int y, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (PanelCore.takeTextureReset()) destroyTexture();
        PanelCore.View v = PanelCore.view(mc.getWindow().getScaleFactor(), new PanelCore.OverlayLoader() {
            @Override public PanelCore.Overlay load() throws Exception { return loadOverlay(); }
        });
        if (v == null) {
            if (PanelCore.progress >= 0) {
                mc.textRenderer.drawWithShadow(m, "Living Inventory: loading " + PanelCore.progress + "%", (float) x, (float) (y - 10), 0xFFFFFFFF);
            }
            return false;
        }

        boolean fresh = false;
        if (v.newTexture || texture == null) {
            destroyTexture();
            texture = new NativeImageBackedTexture(new NativeImage(v.width, v.height, false));
            texture.setFilter(true, false);
            mc.getTextureManager().registerTexture(TEX_ID, texture);
            fresh = true;
        }
        if (v.upload || fresh) {
            NativeImage img = texture.getImage();
            int ow = v.width, oh = v.height;
            for (int yy = 0; yy < oh; yy++)
                for (int xx = 0; xx < ow; xx++)
                    img.setColor(xx, yy, v.pixels[yy * ow + xx]); // ABGR
            texture.upload();
        }
        RenderSystem.setShaderTexture(0, TEX_ID);
        DrawableHelper.drawTexture(m, x, y, w, h, 0f, 0f, v.width, v.height, v.width, v.height);
        return true;
    }
}
