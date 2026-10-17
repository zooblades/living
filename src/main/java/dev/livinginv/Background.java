package dev.livinginv;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads the selected file on a worker thread and draws the current frame. */
public final class Background {
    private static final Identifier TEX_ID = Identifier.of("livinginv", "background");
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "livinginv-decoder");
        t.setDaemon(true);
        return t;
    });

    private static volatile int generation;
    private static volatile GifDecoder.Result data;
    public static volatile String status = "";
    public static volatile int progress = -1; // -1 = idle

    // render-thread only
    private static GifDecoder.Result uploaded;
    private static NativeImageBackedTexture texture;
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
            MinecraftClient.getInstance().getTextureManager().destroyTexture(TEX_ID);
            texture = null;
        }
        uploaded = null;
        shown = -1;
    }

    public static void render(DrawContext ctx) {
        GifDecoder.Result d = data;
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

        if (d == null) {
            if (progress >= 0) {
                ctx.drawTextWithShadow(mc.textRenderer, "Living Inventory: loading " + progress + "%", 40, 8, 0xFFFFFF);
            }
            return;
        }

        if (texture == null || uploaded != d) {
            releaseTexture();
            uploaded = d;
            startMs = Util.getMeasuringTimeMs();
            texture = new NativeImageBackedTexture(new NativeImage(d.width(), d.height(), false));
            texture.setFilter(true, false);
            mc.getTextureManager().registerTexture(TEX_ID, texture);
        }

        // pick frame
        int count = d.frames().length;
        int idx = 0;
        if (count > 1) {
            long total = d.endsMs()[count - 1];
            long pos = (Util.getMeasuringTimeMs() - startMs) % total;
            int lo = 0, hi = count - 1;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (d.endsMs()[mid] > pos) hi = mid; else lo = mid + 1;
            }
            idx = lo;
        }
        if (idx != shown) {
            NativeImage img = texture.getImage();
            int[] px = d.frames()[idx];
            int w = d.width(), h = d.height();
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    img.setColor(x, y, px[y * w + x]); // ABGR in 1.21.1
            texture.upload();
            shown = idx;
        }

        // "cover" fit: fill the window, crop the overflow
        float imgAspect = (float) d.width() / d.height();
        float scrAspect = (float) sw / sh;
        int rw = d.width(), rh = d.height();
        float u = 0, v = 0;
        if (scrAspect > imgAspect) { rh = Math.round(d.width() / scrAspect); v = (d.height() - rh) / 2f; }
        else { rw = Math.round(d.height() * scrAspect); u = (d.width() - rw) / 2f; }
        ctx.drawTexture(TEX_ID, 0, 0, sw, sh, u, v, rw, rh, d.width(), d.height());

        int alpha = Math.round(LivingInvClient.config.dim * 2.55f);
        if (alpha > 0) ctx.fill(0, 0, sw, sh, alpha << 24);
    }
}
