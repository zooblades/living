package dev.livinginv;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads the selected file on a worker thread and draws the current frame in place of the inventory panel. */
public final class Background {
    private static final Identifier TEX_ID = Identifier.fromNamespaceAndPath("livinginv", "background");
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "livinginv-decoder");
        t.setDaemon(true);
        return t;
    });

    private static volatile int generation;
    private static volatile GifDecoder.Result data;
    public static volatile String status = "";
    public static volatile int progress = -1; // -1 = idle
    /** True while InventoryScreen.extractBackground runs (set by a mixin). */
    public static boolean inInventoryBackground;

    // render-thread only
    private static GifDecoder.Result uploaded;
    private static DynamicTexture texture;
    private static int shown = -1;
    private static long startMs;

    private Background() { }

    public static void reload() {
        int gen = ++generation;
        data = null;
        status = "";
        progress = -1;
        releaseTexture();

        Config cfg = LivingInvClient.config;
        if (cfg.selected.isEmpty()) return;
        Path file = LivingInvClient.dir.resolve(cfg.selected);
        if (!Files.isRegularFile(file)) { status = "File not found"; return; }

        progress = 0;
        final int budget = cfg.memoryBudgetMb, side = cfg.maxResolution, secs = cfg.maxDurationSeconds;
        EXEC.submit(() -> {
            try {
                GifDecoder.Result r = GifDecoder.decode(file, budget, side, secs,
                        (done, total) -> { if (gen == generation) progress = total == 0 ? 0 : done * 100 / total; });
                if (gen != generation) return;
                data = r;
                progress = -1;
                if (r.truncated()) {
                    status = String.format("Memory/duration limit: showing %.1fs of the animation (%d frames)",
                            r.totalMs() / 1000.0, r.frames().length);
                }
            } catch (Throwable e) {
                if (gen == generation) { status = "Error: " + e.getMessage(); progress = -1; }
            }
        });
    }

    private static void releaseTexture() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEX_ID);
            texture = null;
        }
        uploaded = null;
        shown = -1;
    }

    /** Returns false if nothing is loaded yet (vanilla panel is drawn meanwhile). */
    public static boolean renderPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        GifDecoder.Result d = data;
        Minecraft mc = Minecraft.getInstance();

        if (d == null) {
            if (progress >= 0) g.text(mc.font, "Living Inventory: loading " + progress + "%", x, y - 10, 0xFFFFFFFF);
            return false;
        }

        if (texture == null || uploaded != d) {
            releaseTexture();
            uploaded = d;
            startMs = System.currentTimeMillis();
            texture = new DynamicTexture(() -> "livinginv_background", new NativeImage(d.width(), d.height(), false));
            mc.getTextureManager().register(TEX_ID, texture);
        }

        int count = d.frames().length;
        int idx = 0;
        if (count > 1) {
            long total = d.endsMs()[count - 1];
            long pos = (System.currentTimeMillis() - startMs) % total;
            int lo = 0, hi = count - 1;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (d.endsMs()[mid] > pos) hi = mid; else lo = mid + 1;
            }
            idx = lo;
        }
        if (idx != shown) {
            NativeImage img = texture.getPixels();
            int[] px = d.frames()[idx];
            int iw = d.width(), ih = d.height();
            for (int yy = 0; yy < ih; yy++)
                for (int xx = 0; xx < iw; xx++)
                    img.setPixelABGR(xx, yy, px[yy * iw + xx]);
            texture.upload();
            shown = idx;
        }

        // "cover" fit into the panel rectangle, crop the overflow
        float imgAspect = (float) d.width() / d.height();
        float panelAspect = (float) w / h;
        int rw = d.width(), rh = d.height();
        float u = 0, v = 0;
        if (panelAspect > imgAspect) { rh = Math.round(d.width() / panelAspect); v = (d.height() - rh) / 2f; }
        else { rw = Math.round(d.height() * panelAspect); u = (d.width() - rw) / 2f; }
        g.blit(RenderPipelines.GUI_TEXTURED, TEX_ID, x, y, u, v, w, h, rw, rh, d.width(), d.height());

        // translucent plates under the slots so items stay readable
        int alpha = Math.round(LivingInvClient.config.slotOpacity * 2.55f);
        if (alpha > 0 && mc.player != null) {
            for (Slot s : mc.player.inventoryMenu.slots) {
                g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, alpha << 24);
            }
        }
        return true;
    }
}
