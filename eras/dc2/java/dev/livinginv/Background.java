package dev.livinginv;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads the selected file on a worker thread and draws the inventory panel 1:1 like vanilla,
 * with only the grey panel fill replaced by the current animation frame (see PanelComposer).
 */
public final class Background {
    private static final Identifier TEX_ID = Identifier.tryParse("livinginv:background");
    private static final Identifier VANILLA_PANEL = Identifier.tryParse("minecraft:textures/gui/container/inventory.png");
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
    private static int[] vanilla;          // vanilla panel pixels (ABGR), vw x vh
    private static int vw, vh;
    private static boolean vanillaValid;
    private static GifDecoder.Result uploaded;
    private static PanelComposer composer;
    private static int composerScale;
    private static int[] buffer;
    private static NativeImageBackedTexture texture;
    private static int shown = -1;
    private static long startMs;

    private Background() { }

    /** Call when the inventory opens: a resource pack may have changed the panel texture. */
    public static void invalidateVanilla() {
        vanillaValid = false;
    }

    public static void reload() {
        int gen = ++generation;
        data = null;
        status = "";
        progress = -1;
        releaseAll();

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

    private static void destroyTexture() {
        if (texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(TEX_ID);
            texture = null;
        }
        shown = -1;
    }

    private static void releaseAll() {
        destroyTexture();
        uploaded = null;
        composer = null;
    }

    private static boolean loadVanilla(MinecraftClient mc) {
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(VANILLA_PANEL);
            if (!res.isPresent()) return false;
            try (InputStream in = res.get().getInputStream(); NativeImage img = NativeImage.read(in)) {
                int k = Math.max(1, img.getWidth() / 256); // resource packs may ship HD panels
                vw = PanelComposer.PANEL_W * k;
                vh = PanelComposer.PANEL_H * k;
                int[] px = new int[vw * vh];
                for (int y = 0; y < vh; y++)
                    for (int x = 0; x < vw; x++)
                        px[y * vw + x] = img.getColor(x, y); // ABGR
                vanilla = px;
                vanillaValid = true;
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns false if nothing is loaded yet (the vanilla panel is drawn meanwhile). */
    public static boolean renderPanel(DrawContext ctx, InventoryScreen screen, int x, int y, int w, int h) {
        GifDecoder.Result d = data;
        MinecraftClient mc = MinecraftClient.getInstance();

        if (d == null) {
            if (progress >= 0) {
                ctx.drawTextWithShadow(mc.textRenderer, "Living Inventory: loading " + progress + "%", x, y - 10, 0xFFFFFFFF);
            }
            return false;
        }
        if (!vanillaValid) {
            if (!loadVanilla(mc)) return false;
            composer = null; // rebuild with the new vanilla pixels
        }

        // one output pixel per physical screen pixel keeps the pixel art sharp
        int scale = Math.max(1, Math.min(4, (int) Math.round(mc.getWindow().getScaleFactor())));
        if (uploaded != d) { uploaded = d; startMs = System.currentTimeMillis(); composer = null; }
        if (composer == null || composerScale != scale || texture == null) {
            destroyTexture();
            composer = new PanelComposer(vanilla, vw, vh, d.width(), d.height(), scale);
            composerScale = scale;
            buffer = new int[composer.outW * composer.outH];
            texture = new NativeImageBackedTexture(new NativeImage(composer.outW, composer.outH, false));
            texture.setFilter(true, false);
            mc.getTextureManager().registerTexture(TEX_ID, texture);
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
            composer.compose(d.frames()[idx], buffer);
            NativeImage img = texture.getImage();
            int ow = composer.outW, oh = composer.outH;
            for (int yy = 0; yy < oh; yy++)
                for (int xx = 0; xx < ow; xx++)
                    img.setColor(xx, yy, buffer[yy * ow + xx]); // ABGR
            texture.upload();
            shown = idx;
        }

        ctx.drawTexture(TEX_ID, x, y, w, h, 0f, 0f, composer.outW, composer.outH, composer.outW, composer.outH);
        return true;
    }
}
