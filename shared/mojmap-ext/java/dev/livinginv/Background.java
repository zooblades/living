package dev.livinginv;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Optional;

/**
 * Draws the inventory panel: the animation underneath, the semi-transparent overlay
 * (assets/livinginv/textures/gui/inventory_overlay.png, replaceable by resource packs) on top.
 * All version-independent logic lives in PanelCore.
 */
public final class Background {
    private static final Identifier TEX_ID = Identifier.fromNamespaceAndPath("livinginv", "background");
    private static final Identifier OVERLAY = Identifier.fromNamespaceAndPath("livinginv", "textures/gui/inventory_overlay.png");
    private static DynamicTexture texture;
    /** True while InventoryScreen.extractBackground runs (set by a mixin). */
    public static boolean inInventoryBackground;

    private Background() { }

    private static void destroyTexture() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEX_ID);
            texture = null;
        }
    }

    private static PanelCore.Overlay loadOverlay() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        Optional<Resource> res = mc.getResourceManager().getResource(OVERLAY);
        if (!res.isPresent()) throw new IllegalStateException("overlay texture missing");
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            int k = Math.max(1, img.getWidth() / 256); // overlay is authored at 256*k px
            int w = PanelComposer.PANEL_W * k, h = PanelComposer.PANEL_H * k;
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    px[y * w + x] = PanelCore.swapRb(img.getPixel(x, y));
            return new PanelCore.Overlay(px, w, h);
        }
    }

    /** Returns false if nothing is loaded yet (the vanilla panel is drawn meanwhile). */
    public static boolean renderPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (PanelCore.takeTextureReset()) destroyTexture();
        PanelCore.View v = PanelCore.view(mc.getWindow().getGuiScale(), Background::loadOverlay);
        if (v == null) {
            if (PanelCore.progress >= 0) {
                g.text(mc.font, "Living Inventory: loading " + PanelCore.progress + "%", x, y - 10, 0xFFFFFFFF);
            }
            return false;
        }

        boolean fresh = false;
        if (v.newTexture || texture == null) {
            destroyTexture();
            texture = new DynamicTexture(() -> "livinginv_background", new NativeImage(v.width, v.height, false));
            mc.getTextureManager().register(TEX_ID, texture);
            fresh = true;
        }
        if (v.upload || fresh) {
            NativeImage img = texture.getPixels();
            int ow = v.width, oh = v.height;
            for (int yy = 0; yy < oh; yy++)
                for (int xx = 0; xx < ow; xx++)
                    img.setPixelABGR(xx, yy, v.pixels[yy * ow + xx]);
            texture.upload();
        }
        g.blit(RenderPipelines.GUI_TEXTURED, TEX_ID, x, y, 0f, 0f, w, h, v.width, v.height, v.width, v.height);
        return true;
    }
}
