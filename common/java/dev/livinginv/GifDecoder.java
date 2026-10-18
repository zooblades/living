package dev.livinginv;

import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Pure-JDK decoder (no Minecraft classes). Composites GIF frames correctly (disposal methods),
 * downsizes them so the whole animation fits into a RAM budget, and returns ABGR pixel arrays
 * ready to be uploaded into a NativeImage.
 */
public final class GifDecoder {
    public static final int MIN_SIDE = 160;

    public static final class Result {
        private final int width, height, sourceFrames;
        private final int[][] frames;
        private final long[] endsMs;
        private final boolean truncated;
        private final long totalMs;

        Result(int width, int height, int[][] frames, long[] endsMs, boolean truncated, int sourceFrames, long totalMs) {
            this.width = width; this.height = height; this.frames = frames; this.endsMs = endsMs;
            this.truncated = truncated; this.sourceFrames = sourceFrames; this.totalMs = totalMs;
        }
        public int width() { return width; }
        public int height() { return height; }
        public int[][] frames() { return frames; }
        public long[] endsMs() { return endsMs; }
        public boolean truncated() { return truncated; }
        public int sourceFrames() { return sourceFrames; }
        public long totalMs() { return totalMs; }
    }

    public interface Progress { void update(int done, int total); }

    public static Result decode(Path file, int budgetMb, int maxSide, int maxSeconds, Progress progress) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) throw new IOException("Cannot open file");
            Iterator<ImageReader> it = ImageIO.getImageReaders(in);
            if (!it.hasNext()) throw new IOException("Unsupported format");
            ImageReader reader = it.next();
            try {
                reader.setInput(in, false, false);
                return decode(reader, budgetMb, maxSide, maxSeconds, progress);
            } finally {
                reader.dispose();
            }
        }
    }

    private static Result decode(ImageReader r, int budgetMb, int maxSide, int maxSeconds, Progress progress) throws IOException {
        boolean gif = "gif".equalsIgnoreCase(r.getFormatName());
        int n = gif ? r.getNumImages(true) : 1;
        if (n <= 0) throw new IOException("No frames");

        // Logical screen size
        int lw = 0, lh = 0;
        if (gif) {
            IIOMetadata sm = r.getStreamMetadata();
            if (sm != null) {
                Node lsd = child(sm.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
                lw = attr(lsd, "logicalScreenWidth", 0);
                lh = attr(lsd, "logicalScreenHeight", 0);
            }
        }
        if (lw <= 0 || lh <= 0) { lw = r.getWidth(0); lh = r.getHeight(0); }

        // Pick stored resolution so that all frames fit the budget
        long budget = budgetMb * 1024L * 1024L;
        int side = Math.min(maxSide, Math.max(lw, lh));
        int tw, th;
        while (true) {
            float s = (float) side / Math.max(lw, lh);
            tw = Math.max(1, Math.round(lw * s));
            th = Math.max(1, Math.round(lh * s));
            if ((long) n * tw * th * 4 <= budget || side <= MIN_SIDE) break;
            side = Math.max(MIN_SIDE, (int) (side * 0.85f));
        }
        int maxFrames = (int) Math.max(1, Math.min(Integer.MAX_VALUE, budget / ((long) tw * th * 4)));
        long maxMs = maxSeconds * 1000L;

        BufferedImage canvas = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_ARGB);
        BufferedImage saved = null;
        String prevDisposal = "none";
        int px = 0, py = 0, pw = 0, ph = 0;

        List<int[]> frames = new ArrayList<>();
        List<Long> ends = new ArrayList<>();
        long t = 0;
        boolean truncated = false;

        for (int i = 0; i < n; i++) {
            if (frames.size() >= maxFrames || (i > 0 && t >= maxMs)) { truncated = true; break; }

            BufferedImage fr = r.read(i);
            int left = 0, top = 0, delayCs = 10;
            String disposal = "none";
            if (gif) {
                Node root = r.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                Node desc = child(root, "ImageDescriptor");
                left = attr(desc, "imageLeftPosition", 0);
                top = attr(desc, "imageTopPosition", 0);
                Node gce = child(root, "GraphicControlExtension");
                if (gce != null) {
                    delayCs = attr(gce, "delayTime", 10);
                    Node d = gce.getAttributes().getNamedItem("disposalMethod");
                    if (d != null) disposal = d.getNodeValue();
                }
            }
            if (delayCs <= 1) delayCs = 10; // browsers do the same for 0/1 cs

            // Dispose previous frame
            if (i > 0) {
                if ("restoreToBackgroundColor".equals(prevDisposal)) {
                    Graphics2D g = canvas.createGraphics();
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(px, py, pw, ph);
                    g.dispose();
                } else if ("restoreToPrevious".equals(prevDisposal) && saved != null) {
                    Graphics2D g = canvas.createGraphics();
                    g.setComposite(AlphaComposite.Src);
                    g.drawImage(saved, 0, 0, null);
                    g.dispose();
                }
            }
            if ("restoreToPrevious".equals(disposal)) {
                saved = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = saved.createGraphics();
                g.setComposite(AlphaComposite.Src);
                g.drawImage(canvas, 0, 0, null);
                g.dispose();
            }

            Graphics2D g = canvas.createGraphics();
            g.drawImage(fr, left, top, null);
            g.dispose();
            px = left; py = top; pw = fr.getWidth(); ph = fr.getHeight();
            prevDisposal = disposal;

            BufferedImage scaled = (tw == lw && th == lh) ? canvas : scaleTo(canvas, tw, th);
            int[] argb = scaled.getRGB(0, 0, tw, th, null, 0, tw);
            for (int k = 0; k < argb.length; k++) {
                int c = argb[k];
                argb[k] = (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF); // ARGB -> ABGR
            }
            frames.add(argb);
            t += delayCs * 10L;
            ends.add(t);
            if (progress != null) progress.update(i + 1, Math.min(n, maxFrames));
        }

        long[] endArr = new long[ends.size()];
        for (int i = 0; i < endArr.length; i++) endArr[i] = ends.get(i);
        return new Result(tw, th, frames.toArray(new int[0][]), endArr, truncated, n, t);
    }

    private static BufferedImage scaleTo(BufferedImage src, int tw, int th) {
        BufferedImage cur = src;
        while (cur.getWidth() / 2 >= tw && cur.getHeight() / 2 >= th) {
            cur = resize(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        }
        return resize(cur, tw, th);
    }

    private static BufferedImage resize(BufferedImage s, int w, int h) {
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(s, 0, 0, w, h, null);
        g.dispose();
        return o;
    }

    private static Node child(Node parent, String name) {
        if (parent == null) return null;
        for (Node c = parent.getFirstChild(); c != null; c = c.getNextSibling())
            if (c.getNodeName().equals(name)) return c;
        return null;
    }

    private static int attr(Node n, String name, int def) {
        if (n == null || n.getAttributes() == null) return def;
        Node a = n.getAttributes().getNamedItem(name);
        if (a == null) return def;
        try { return Integer.parseInt(a.getNodeValue()); } catch (NumberFormatException e) { return def; }
    }
}
