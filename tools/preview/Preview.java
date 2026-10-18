import dev.livinginv.PanelComposer;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class Preview {
    static int swap(int c) { return (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF); }
    public static void main(String[] a) throws Exception {
        BufferedImage ovImg = ImageIO.read(new File(a[0]));
        BufferedImage fr = ImageIO.read(new File(a[1]));
        int S = Integer.parseInt(a[3]);
        int k = Math.max(1, ovImg.getWidth() / 256), ow = 176 * k, oh = 166 * k;
        int[] ov = new int[ow * oh];
        for (int y = 0; y < oh; y++) for (int x = 0; x < ow; x++) ov[y * ow + x] = swap(ovImg.getRGB(x, y));
        int fw = fr.getWidth(), fh = fr.getHeight();
        int[] frame = new int[fw * fh];
        for (int y = 0; y < fh; y++) for (int x = 0; x < fw; x++) frame[y * fw + x] = swap(fr.getRGB(x, y) | 0xFF000000);
        PanelComposer pc = new PanelComposer(ov, ow, oh, fw, fh, S);
        int[] out = new int[pc.outW * pc.outH];
        long t = System.nanoTime();
        for (int i = 0; i < 20; i++) pc.compose(frame, out);
        System.out.println("compose ms/frame: " + (System.nanoTime() - t) / 20 / 1e6 + " size " + pc.outW + "x" + pc.outH);
        int gap = 20;
        BufferedImage res = new BufferedImage(pc.outW + gap * 2, pc.outH + gap * 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < res.getHeight(); y++) for (int x = 0; x < res.getWidth(); x++)
            res.setRGB(x, y, ((x / 16 + y / 16) % 2 == 0) ? 0xFF33503A : 0xFF2B4431); // "world" checker, shows transparency
        for (int y = 0; y < pc.outH; y++) for (int x = 0; x < pc.outW; x++) {
            int c = swap(out[y * pc.outW + x]), al = c >>> 24;
            if (al == 0) continue;
            int d = res.getRGB(gap + x, gap + y);
            int r = ((c >> 16 & 255) * al + (d >> 16 & 255) * (255 - al)) / 255;
            int g = ((c >> 8 & 255) * al + (d >> 8 & 255) * (255 - al)) / 255;
            int b = ((c & 255) * al + (d & 255) * (255 - al)) / 255;
            res.setRGB(gap + x, gap + y, 0xFF000000 | r << 16 | g << 8 | b);
        }
        ImageIO.write(res, "png", new File(a[2]));
    }
}
