package dev.livinginv;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Version-independent part of the inventory renderer: background decoding, frame timing and panel
 * composition. The per-version Background class only loads the overlay pixels, owns the GPU texture and
 * draws it. No Minecraft classes here.
 */
public final class PanelCore {
    public static volatile String status = "";
    public static volatile int progress = -1; // -1 = idle

    /** Overlay pixels for the panel region: ABGR, width 176*k, height 166*k. */
    public static final class Overlay {
        public final int[] px;
        public final int w, h;
        public Overlay(int[] px, int w, int h) { this.px = px; this.w = w; this.h = h; }
    }

    public interface OverlayLoader { Overlay load() throws Exception; }

    /** What the renderer has to do this frame. */
    public static final class View {
        public int width, height;
        /** (Re)create the GPU texture with width x height. */
        public boolean newTexture;
        /** Copy pixels (ABGR, width*height) into the texture. */
        public boolean upload;
        public int[] pixels;
    }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "livinginv-decoder");
            t.setDaemon(true);
            return t;
        }
    });

    private static volatile int generation;
    private static volatile GifDecoder.Result data;

    // render-thread state
    private static Overlay overlay;
    private static boolean overlayValid;
    private static GifDecoder.Result composedFor;
    private static PanelComposer composer;
    private static int composerScale;
    private static int[] buffer;
    private static int shown = -1;
    private static int composedOpacity = -1;
    private static long startMs;
    private static boolean textureReset;
    private static final View VIEW = new View();

    private PanelCore() { }

    /** (Re)loads the file selected in the config. Call from the client thread. */
    public static void reload() {
        final int gen = ++generation;
        data = null;
        status = "";
        progress = -1;
        composer = null;
        composedFor = null;
        shown = -1;
        textureReset = true;

        final Config cfg = LivingInvClient.config;
        if (cfg.selected.isEmpty()) return;
        final Path file = LivingInvClient.dir.resolve(cfg.selected);
        if (!Files.isRegularFile(file)) { status = "File not found"; return; }

        progress = 0;
        final int budget = cfg.memoryBudgetMb, side = cfg.maxResolution, secs = cfg.maxDurationSeconds;
        EXEC.submit(new Runnable() {
            @Override public void run() {
                try {
                    GifDecoder.Result r = GifDecoder.decode(file, budget, side, secs, new GifDecoder.Progress() {
                        @Override public void update(int done, int total) {
                            if (gen == generation) progress = total == 0 ? 0 : done * 100 / total;
                        }
                    });
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
            }
        });
    }

    /** The overlay texture may have changed (resource pack): reload it next frame. Call when the inventory opens. */
    public static void invalidateOverlay() {
        overlayValid = false;
    }

    /** True once per reload/overlay change: the renderer must destroy its GPU texture now. */
    public static boolean takeTextureReset() {
        boolean r = textureReset;
        textureReset = false;
        return r;
    }

    /** Called every frame by the renderer. Returns null while nothing is ready (vanilla panel is drawn). */
    public static View view(double guiScale, OverlayLoader loader) {
        GifDecoder.Result d = data;
        if (d == null) return null;

        if (!overlayValid) {
            try {
                overlay = loader.load();
            } catch (Exception e) {
                overlay = null;
                status = "Overlay error: " + e;
            }
            overlayValid = overlay != null;
            if (!overlayValid) return null;
            composer = null;
        }

        int scale = Math.max(1, Math.min(4, (int) Math.round(guiScale)));
        if (composedFor != d) { composedFor = d; startMs = System.currentTimeMillis(); composer = null; }

        VIEW.newTexture = false;
        if (composer == null || composerScale != scale) {
            composer = new PanelComposer(overlay.px, overlay.w, overlay.h, d.width(), d.height(), scale);
            composerScale = scale;
            buffer = new int[composer.outW * composer.outH];
            shown = -1;
            VIEW.newTexture = true;
        }

        int count = d.frames().length, idx = 0;
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
        int opacity = LivingInvClient.config.overlayOpacity;
        if (opacity != composedOpacity) { composedOpacity = opacity; shown = -1; }
        VIEW.upload = idx != shown;
        if (VIEW.upload) {
            composer.compose(d.frames()[idx], buffer, opacity);
            shown = idx;
        }
        VIEW.width = composer.outW;
        VIEW.height = composer.outH;
        VIEW.pixels = buffer;
        return VIEW;
    }

    /** Swaps the red and blue channels (ARGB <-> ABGR). */
    public static int swapRb(int c) {
        return (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
    }
}
