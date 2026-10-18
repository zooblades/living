package dev.livinginv;

/**
 * Builds the inventory panel 1:1 from the vanilla panel pixels, replacing ONLY the plain panel fill
 * (#C6C6C6) with the current animation frame. Borders, bevels, rounded corners, slots, the player box
 * and the crafting arrow stay exactly as in vanilla. No Minecraft classes here, so it can be unit-tested.
 *
 * All colours are ABGR ints (the NativeImage layout); the fill colour is grey so the order does not matter.
 */
public final class PanelComposer {
    /** Panel size in GUI pixels. */
    public static final int PANEL_W = 176, PANEL_H = 166;
    /** Vanilla panel fill colour (opaque light grey). */
    public static final int FILL = 0xFFC6C6C6;

    public final int outW, outH;
    private final int[] map;   // index into the animation frame, or -1 = use fixed[]
    private final int[] fixed; // vanilla pixel (alpha 0 in the rounded corners)

    /**
     * @param vanilla vanilla texture region of the panel (vw x vh texels, ABGR), top-left aligned
     * @param frameW  animation frame width
     * @param frameH  animation frame height
     * @param scale   output pixels per GUI pixel (use the GUI scale for 1:1 sharpness)
     */
    public PanelComposer(int[] vanilla, int vw, int vh, int frameW, int frameH, int scale) {
        outW = PANEL_W * scale;
        outH = PANEL_H * scale;
        map = new int[outW * outH];
        fixed = new int[outW * outH];

        // "cover" crop of the frame to the panel aspect ratio
        float panelAspect = (float) PANEL_W / PANEL_H, imgAspect = (float) frameW / frameH;
        float cu = 0, cv = 0, cw = frameW, ch = frameH;
        if (panelAspect > imgAspect) { ch = frameW / panelAspect; cv = (frameH - ch) / 2f; }
        else { cw = frameH * panelAspect; cu = (frameW - cw) / 2f; }

        for (int y = 0; y < outH; y++) {
            int vy = Math.min(vh - 1, (int) ((y + 0.5f) * vh / outH));
            for (int x = 0; x < outW; x++) {
                int vx = Math.min(vw - 1, (int) ((x + 0.5f) * vw / outW));
                int c = vanilla[vy * vw + vx];
                int i = y * outW + x;
                if (c == FILL) {
                    int sx = Math.min(frameW - 1, (int) (cu + (x + 0.5f) * cw / outW));
                    int sy = Math.min(frameH - 1, (int) (cv + (y + 0.5f) * ch / outH));
                    map[i] = sy * frameW + sx;
                } else {
                    map[i] = -1;
                    fixed[i] = c;
                }
            }
        }
    }

    /** Writes the composed panel for the given animation frame into out (length outW*outH). */
    public void compose(int[] frame, int[] out) {
        for (int i = 0; i < out.length; i++) {
            int s = map[i];
            out[i] = s >= 0 ? frame[s] : fixed[i];
        }
    }
}
