package dev.livinginv;

/**
 * Composes the inventory panel from a semi-transparent OVERLAY (frame, slot plates, ...) drawn over the
 * current animation frame. The GIF is shown only inside the panel silhouette: transparent overlay pixels
 * that can be reached from the border of the overlay are "outside" (rounded corners) and stay transparent,
 * every other transparent pixel (the panel fill, the player box hole, ...) shows the animation.
 *
 * No Minecraft classes here, so it can be unit-tested. All colours are ABGR ints (NativeImage layout).
 */
public final class PanelComposer {
    /** Panel size in GUI pixels. */
    public static final int PANEL_W = 176, PANEL_H = 166;

    private static final byte OUTSIDE = 0, CLEAR = 1, BLEND = 2, SOLID = 3;

    public final int outW, outH;
    private final byte[] type;
    private final int[] map;   // frame pixel index (CLEAR / BLEND)
    private final int[] ov;    // overlay colour (BLEND / SOLID)

    /**
     * @param overlay overlay region of the panel (ow x oh texels, ABGR), top-left aligned, ow = 176*k
     * @param scale   output pixels per GUI pixel (use the GUI scale for 1:1 sharpness)
     */
    public PanelComposer(int[] overlay, int ow, int oh, int frameW, int frameH, int scale) {
        outW = PANEL_W * scale;
        outH = PANEL_H * scale;
        int n = outW * outH;
        type = new byte[n];
        map = new int[n];
        ov = new int[n];

        boolean[] outside = floodOutside(overlay, ow, oh);

        // "cover" crop of the frame to the panel aspect ratio
        float panelAspect = (float) PANEL_W / PANEL_H, imgAspect = (float) frameW / frameH;
        float cu = 0, cv = 0, cw = frameW, ch = frameH;
        if (panelAspect > imgAspect) { ch = frameW / panelAspect; cv = (frameH - ch) / 2f; }
        else { cw = frameH * panelAspect; cu = (frameW - cw) / 2f; }

        for (int y = 0; y < outH; y++) {
            int oy = Math.min(oh - 1, (int) ((y + 0.5f) * oh / outH));
            for (int x = 0; x < outW; x++) {
                int ox = Math.min(ow - 1, (int) ((x + 0.5f) * ow / outW));
                int oi = oy * ow + ox, i = y * outW + x;
                int c = overlay[oi], a = c >>> 24;
                if (outside[oi]) { type[i] = OUTSIDE; continue; }
                ov[i] = c;
                if (a == 255) { type[i] = SOLID; continue; }
                type[i] = a == 0 ? CLEAR : BLEND;
                int sx = Math.min(frameW - 1, (int) (cu + (x + 0.5f) * cw / outW));
                int sy = Math.min(frameH - 1, (int) (cv + (y + 0.5f) * ch / outH));
                map[i] = sy * frameW + sx;
            }
        }
    }

    /** Transparent pixels connected (4-neighbourhood) to the border of the overlay region. */
    private static boolean[] floodOutside(int[] px, int w, int h) {
        boolean[] out = new boolean[w * h];
        int[] q = new int[w * h];
        int head = 0, tail = 0;
        for (int x = 0; x < w; x++) {
            tail = seed(px, out, q, tail, x, 0, w);
            tail = seed(px, out, q, tail, x, h - 1, w);
        }
        for (int y = 0; y < h; y++) {
            tail = seed(px, out, q, tail, 0, y, w);
            tail = seed(px, out, q, tail, w - 1, y, w);
        }
        while (head < tail) {
            int p = q[head++], x = p % w, y = p / w;
            if (x > 0) tail = seed(px, out, q, tail, x - 1, y, w);
            if (x < w - 1) tail = seed(px, out, q, tail, x + 1, y, w);
            if (y > 0) tail = seed(px, out, q, tail, x, y - 1, w);
            if (y < h - 1) tail = seed(px, out, q, tail, x, y + 1, w);
        }
        return out;
    }

    private static int seed(int[] px, boolean[] out, int[] q, int tail, int x, int y, int w) {
        int i = y * w + x;
        if (!out[i] && (px[i] >>> 24) == 0) { out[i] = true; q[tail++] = i; }
        return tail;
    }

    /** Writes the composed panel for the given animation frame into out (length outW*outH). */
    public void compose(int[] frame, int[] out) {
        for (int i = 0; i < out.length; i++) {
            switch (type[i]) {
                case OUTSIDE: out[i] = 0; break;
                case CLEAR: out[i] = frame[map[i]] | 0xFF000000; break;
                case SOLID: out[i] = ov[i]; break;
                default: {
                    int g = frame[map[i]], o = ov[i], a = o >>> 24, inv = 255 - a;
                    int r = ((o & 255) * a + (g & 255) * inv + 127) / 255;
                    int gr = (((o >> 8) & 255) * a + ((g >> 8) & 255) * inv + 127) / 255;
                    int b = (((o >> 16) & 255) * a + ((g >> 16) & 255) * inv + 127) / 255;
                    out[i] = 0xFF000000 | b << 16 | gr << 8 | r;
                }
            }
        }
    }
}
