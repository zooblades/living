package dev.livinginv;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The mod menu: two tabs (animations, overlays), scrollable lists and a live overlay preview. */
public class PickerScreen extends Screen {
    private static final int ROW = 24;
    private static final int BG = 0xF0121820, BORDER = 0xFF2C3947, HEAD_TOP = 0xFF1F2A35, HEAD_BOTTOM = 0xFF161E26;
    private static final int ACCENT = 0xFF4FD1C5, TEXT = 0xFFE8EEF4, DIM = 0xFF8A98A6, WARN = 0xFFFFB454;
    private static final int ROW_HOVER = 0x26FFFFFF, ROW_SELECTED = 0x4036B5A5;

    private static final class Item {
        final String id, label, detail;
        final boolean selected;
        Item(String id, String label, String detail, boolean selected) {
            this.id = id; this.label = label; this.detail = detail; this.selected = selected;
        }
    }

    private static final class Preview {
        Identifier id;
        int regionW, regionH, texW, texH;
        boolean failed;
    }

    private static int lastTab; // remembered between openings: 0 = animations, 1 = overlays

    private final Screen parent;
    private int tab = lastTab;
    private List<Item> items = new ArrayList<>();
    private double scroll;
    private final Map<String, Preview> previews = new HashMap<>();
    private int px, py, pw, ph, listX, listY, listW, listH, rightX, rightW;

    public PickerScreen(Screen parent) {
        super(Text.translatable("livinginv.title"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- data

    static boolean supportedAnimation(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".gif") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp");
    }

    private void buildItems() {
        Config cfg = LivingInvClient.config;
        List<Item> out = new ArrayList<>();
        if (tab == 0) {
            try (Stream<Path> s = Files.list(LivingInvClient.dir)) {
                List<Path> files = s.filter(Files::isRegularFile).filter(PickerScreen::supportedAnimation).sorted().collect(Collectors.toList());
                for (Path f : files) {
                    String name = f.getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    String ext = dot > 0 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "";
                    long kb = 0;
                    try { kb = Files.size(f) / 1024; } catch (IOException ignored) { }
                    String size = kb >= 1024 ? String.format(Locale.ROOT, "%.1f MB", kb / 1024.0) : kb + " KB";
                    out.add(new Item(name, dot > 0 ? name.substring(0, dot) : name, ext + "  \u00b7  " + size, name.equals(cfg.selected)));
                }
            } catch (IOException ignored) { }
        } else {
            for (Overlays.Entry e : Overlays.list()) {
                String label = e.name != null ? e.name : Text.translatable("livinginv.default").getString();
                String src = Text.translatable(e.user ? "livinginv.source.user" : "livinginv.source.builtin").getString();
                out.add(new Item(e.id, label, src, e.id.equals(cfg.overlay)));
            }
        }
        items = out;
    }

    private void select(Item it) {
        Config cfg = LivingInvClient.config;
        if (tab == 0) {
            cfg.selected = it.id;
            LivingInvClient.saveConfig();
            PanelCore.reload();
        } else {
            cfg.overlay = it.id;
            LivingInvClient.saveConfig();
            PanelCore.invalidateOverlay();
        }
        buildItems();
    }

    /** Test hooks. */
    public void debugTab(int t) { switchTab(t); }

    public void debugSelect(int index) {
        if (index >= 0 && index < items.size()) select(items.get(index));
    }

    private void switchTab(int t) {
        if (t == tab) return;
        tab = t;
        lastTab = t;
        scroll = 0;
        clearAndInit();
    }

    // ---------------------------------------------------------------- layout & widgets

    @Override
    protected void init() {
        buildItems();
        pw = Math.min(width - 16, 384);
        ph = Math.min(height - 12, 226);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listX = px + 10;
        listY = py + 46;
        listW = (int) ((pw - 30) * 0.5f);
        listH = ph - 46 - 36;
        rightX = listX + listW + 10;
        rightW = px + pw - 10 - rightX;
        clampScroll();

        final Config cfg = LivingInvClient.config;
        int by = py + ph - 28, bw = (pw - 20 - 8) / 3;
        addDrawableChild(ButtonWidget.builder(Text.translatable("livinginv.folder"),
                b -> FolderOpener.open(tab == 0 ? LivingInvClient.dir : Overlays.userDir()))
                .dimensions(px + 10, by, bw, 20).build());
        ButtonWidget mid = addDrawableChild(ButtonWidget.builder(
                Text.translatable(tab == 0 ? "livinginv.stop" : "livinginv.reset_overlay"), b -> {
                    if (tab == 0) { cfg.selected = ""; LivingInvClient.saveConfig(); PanelCore.reload(); }
                    else { cfg.overlay = ""; LivingInvClient.saveConfig(); PanelCore.invalidateOverlay(); }
                    clearAndInit();
                }).dimensions(px + 10 + bw + 4, by, bw, 20).build());
        mid.active = tab == 0 ? !cfg.selected.isEmpty() : !cfg.overlay.isEmpty();
        addDrawableChild(ButtonWidget.builder(Text.translatable("livinginv.done"), b -> close())
                .dimensions(px + 10 + (bw + 4) * 2, by, bw, 20).build());
    }

    private int contentHeight() { return items.size() * ROW; }

    private void clampScroll() {
        double max = Math.max(0, contentHeight() - listH);
        scroll = Math.max(0, Math.min(scroll, max));
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        for (int t = 0; t < 2; t++) {
            int tx = px + 10 + t * 96;
            if (mx >= tx && mx < tx + 92 && my >= py + 26 && my < py + 42) { switchTab(t); return true; }
        }
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int idx = (int) ((my - listY + scroll) / ROW);
            if (idx >= 0 && idx < items.size()) { select(items.get(idx)); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (mx >= listX && mx < listX + listW + 6 && my >= listY && my < listY + listH) {
            scroll -= vertical * ROW;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    // Screen declares one of these two names depending on the Minecraft version; the other one is just a spare method.
    public void onFilesDropped(List<Path> paths) { handleDrop(paths); }

    public void filesDragged(List<Path> paths) { handleDrop(paths); }

    private void handleDrop(List<Path> paths) {
        Path target = tab == 0 ? LivingInvClient.dir : Overlays.userDir();
        for (Path p : paths) {
            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean ok = tab == 0 ? supportedAnimation(p) : n.endsWith(".png");
            if (!ok) continue;
            try {
                Files.createDirectories(target);
                Files.copy(p, target.resolve(p.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) { }
        }
        buildItems();
        clampScroll();
    }

    @Override
    public void removed() {
        for (Preview p : previews.values()) {
            if (p.id != null) client.getTextureManager().destroyTexture(p.id);
        }
        previews.clear();
        super.removed();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    // ---------------------------------------------------------------- drawing

    /** All custom drawing happens here so that the vanilla buttons are rendered on top of it. */
    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        super.renderBackground(ctx, mx, my, delta);

        // panel
        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, BORDER);
        ctx.fill(px, py, px + pw, py + ph, BG);
        ctx.fillGradient(px, py, px + pw, py + 44, HEAD_TOP, HEAD_BOTTOM);
        ctx.fill(px, py + 44, px + pw, py + 45, BORDER);
        ctx.drawTextWithShadow(textRenderer, title, px + 12, py + 9, ACCENT);

        // tabs
        String[] keys = {"livinginv.tab.animations", "livinginv.tab.overlays"};
        for (int t = 0; t < 2; t++) {
            int tx = px + 10 + t * 96;
            boolean active = t == tab;
            boolean hot = mx >= tx && mx < tx + 92 && my >= py + 26 && my < py + 42;
            if (hot && !active) ctx.fill(tx, py + 26, tx + 92, py + 42, ROW_HOVER);
            Text label = Text.translatable(keys[t]);
            ctx.drawTextWithShadow(textRenderer, label, tx + (92 - textRenderer.getWidth(label)) / 2, py + 30, active ? TEXT : DIM);
            if (active) ctx.fill(tx, py + 41, tx + 92, py + 44, ACCENT);
        }

        drawList(ctx, mx, my);
        drawRightPane(ctx, mx, my);
    }

    private int hoveredIndex(int mx, int my) {
        if (mx < listX || mx >= listX + listW || my < listY || my >= listY + listH) return -1;
        int idx = (int) ((my - listY + scroll) / ROW);
        return idx >= 0 && idx < items.size() ? idx : -1;
    }

    private void drawList(DrawContext ctx, int mx, int my) {
        ctx.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, BORDER);
        ctx.fill(listX, listY, listX + listW, listY + listH, 0xFF0E131A);

        if (items.isEmpty()) {
            Text msg = Text.translatable(tab == 0 ? "livinginv.empty.anim" : "livinginv.empty.overlay");
            ctx.drawTextWithShadow(textRenderer, msg, listX + (listW - textRenderer.getWidth(msg)) / 2, listY + listH / 2 - 4, DIM);
            return;
        }
        int hover = hoveredIndex(mx, my);
        ctx.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < items.size(); i++) {
            int y = listY + i * ROW - (int) scroll;
            if (y + ROW < listY || y > listY + listH) continue;
            Item it = items.get(i);
            if (it.selected) ctx.fill(listX, y, listX + listW, y + ROW, ROW_SELECTED);
            else if (i == hover) ctx.fill(listX, y, listX + listW, y + ROW, ROW_HOVER);
            if (it.selected) ctx.fill(listX, y + 2, listX + 3, y + ROW - 2, ACCENT);
            int tw = listW - 20;
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(it.label, tw), listX + 9, y + 4, it.selected ? ACCENT : TEXT);
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(it.detail, tw), listX + 9, y + 14, DIM);
            ctx.fill(listX + 6, y + ROW - 1, listX + listW - 6, y + ROW, 0x14FFFFFF);
        }
        ctx.disableScissor();

        int content = contentHeight();
        if (content > listH) {
            int thumb = Math.max(16, listH * listH / content);
            int ty = listY + (int) ((listH - thumb) * (scroll / (content - listH)));
            ctx.fill(listX + listW - 4, listY + 2, listX + listW - 2, listY + listH - 2, 0x22FFFFFF);
            ctx.fill(listX + listW - 4, ty, listX + listW - 2, ty + thumb, ACCENT);
        }
    }

    private void drawRightPane(DrawContext ctx, int mx, int my) {
        if (tab == 0) drawAnimationInfo(ctx); else drawOverlayPreview(ctx, mx, my);
        Text hint = Text.translatable(tab == 0 ? "livinginv.drop.anim" : "livinginv.drop.overlay");
        List<OrderedText> lines = textRenderer.wrapLines(hint, rightW);
        int y = listY + listH - lines.size() * 10;
        for (OrderedText l : lines) { ctx.drawTextWithShadow(textRenderer, l, rightX, y, DIM); y += 10; }
    }

    private void drawAnimationInfo(DrawContext ctx) {
        Config cfg = LivingInvClient.config;
        int y = listY + 2;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("livinginv.current"), rightX, y, DIM);
        y += 12;
        String name = cfg.selected.isEmpty() ? Text.translatable("livinginv.none").getString() : cfg.selected;
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(name, rightW), rightX, y, cfg.selected.isEmpty() ? DIM : TEXT);
        y += 18;

        if (PanelCore.progress >= 0) {
            ctx.fill(rightX, y, rightX + rightW, y + 8, 0xFF0E131A);
            ctx.fill(rightX, y, rightX + rightW * PanelCore.progress / 100, y + 8, ACCENT);
            ctx.drawBorder(rightX, y, rightW, 8, BORDER);
            y += 14;
            ctx.drawTextWithShadow(textRenderer, PanelCore.progress + "%", rightX, y, DIM);
            y += 14;
        }
        String st = PanelCore.status;
        if (!st.isEmpty()) {
            for (OrderedText l : textRenderer.wrapLines(Text.literal(st), rightW)) {
                ctx.drawTextWithShadow(textRenderer, l, rightX, y, WARN);
                y += 10;
            }
            y += 4;
        }
        for (OrderedText l : textRenderer.wrapLines(Text.translatable("livinginv.tip.anim"), rightW)) {
            if (y > listY + listH - 34) break;
            ctx.drawTextWithShadow(textRenderer, l, rightX, y, DIM);
            y += 10;
        }
    }

    private Preview preview(Item it) {
        Preview p = previews.get(it.id);
        if (p != null) return p;
        p = new Preview();
        previews.put(it.id, p);
        try (InputStream in = Overlays.open(it.id)) {
            NativeImage img = NativeImage.read(in);
            int k = Math.max(1, img.getWidth() / 256);
            p.texW = img.getWidth();
            p.texH = img.getHeight();
            p.regionW = Math.min(p.texW, PanelComposer.PANEL_W * k);
            p.regionH = Math.min(p.texH, PanelComposer.PANEL_H * k);
            p.id = Identifier.tryParse("livinginv:preview/" + Integer.toHexString(it.id.hashCode()));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            client.getTextureManager().registerTexture(p.id, tex);
        } catch (Exception e) {
            p.failed = true;
        }
        return p;
    }

    private void drawOverlayPreview(DrawContext ctx, int mx, int my) {
        if (items.isEmpty()) return;
        int hover = hoveredIndex(mx, my);
        Item it = hover >= 0 ? items.get(hover) : null;
        if (it == null) for (Item i : items) if (i.selected) { it = i; break; }
        if (it == null) it = items.get(0);

        int availH = listH - 14 - 24;
        float s = Math.min(rightW / (float) PanelComposer.PANEL_W, availH / (float) PanelComposer.PANEL_H);
        int dw = (int) (PanelComposer.PANEL_W * s), dh = (int) (PanelComposer.PANEL_H * s);
        int ox = rightX + (rightW - dw) / 2, oy = listY + 2;

        // checkerboard shows which parts of the overlay are transparent
        for (int cy = 0; cy < dh; cy += 6)
            for (int cx = 0; cx < dw; cx += 6) {
                boolean a = ((cx / 6) + (cy / 6)) % 2 == 0;
                ctx.fill(ox + cx, oy + cy, Math.min(ox + dw, ox + cx + 6), Math.min(oy + dh, oy + cy + 6), a ? 0xFF2A3440 : 0xFF222B35);
            }
        Preview p = preview(it);
        if (p.id != null && !p.failed) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            ctx.drawTexture(p.id, ox, oy, dw, dh, 0f, 0f, p.regionW, p.regionH, p.texW, p.texH);
            RenderSystem.disableBlend();
        }
        ctx.drawBorder(ox - 1, oy - 1, dw + 2, dh + 2, BORDER);
        String cap = textRenderer.trimToWidth(it.label, rightW);
        ctx.drawTextWithShadow(textRenderer, cap, rightX + (rightW - textRenderer.getWidth(cap)) / 2, oy + dh + 6, it.selected ? ACCENT : TEXT);
    }
}
