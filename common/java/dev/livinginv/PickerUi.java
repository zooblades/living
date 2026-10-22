package dev.livinginv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The whole mod menu, independent of the Minecraft version: tabs, scrollable lists, overlay preview with an
 * opacity slider, flat buttons and a small rename dialog. A per-version "platform" only draws rectangles/text
 * and a preview texture; a thin Screen subclass forwards the input events.
 */
public final class PickerUi {

    /** What the menu needs from the game. */
    public interface Platform {
        void fill(int x1, int y1, int x2, int y2, int argb);

        void text(String s, int x, int y, int argb);

        int textWidth(String s);

        /** Draws the panel region of overlay {@code id} into the box, multiplied by alphaPct (0..100). */
        void drawPreview(String id, int x, int y, int w, int h, int alphaPct);

        void releasePreviews();

        String tr(String key);

        String clipboard();

        /** Leave the menu (return to the parent screen). */
        void close();
    }

    private static final int ROW = 24;
    private static final int BG = 0xF0121820, BORDER = 0xFF2C3947, HEAD_TOP = 0xFF1F2A35, HEAD_BOTTOM = 0xFF161E26;
    private static final int ACCENT = 0xFF4FD1C5, TEXT = 0xFFE8EEF4, DIM = 0xFF8A98A6, WARN = 0xFFFFB454;
    private static final int ROW_HOVER = 0x26FFFFFF, ROW_SELECTED = 0x4036B5A5;
    private static final int KEY_ESC = 256, KEY_ENTER = 257, KEY_BACKSPACE = 259, KEY_RIGHT = 262, KEY_LEFT = 263,
            KEY_DELETE = 261, KEY_HOME = 268, KEY_END = 269, KEY_KP_ENTER = 335, KEY_V = 86;

    private static final class Item {
        final String id, label, detail;
        final boolean selected;
        Item(String id, String label, String detail, boolean selected) {
            this.id = id; this.label = label; this.detail = detail; this.selected = selected;
        }
    }

    private static final class Btn {
        final String label;
        final int x, y, w, h;
        final boolean enabled, primary;
        final Runnable action;
        Btn(String label, int x, int y, int w, int h, boolean enabled, boolean primary, Runnable action) {
            this.label = label; this.x = x; this.y = y; this.w = w; this.h = h;
            this.enabled = enabled; this.primary = primary; this.action = action;
        }
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private static int lastTab; // 0 = animations, 1 = overlays (remembered between openings)

    private final Platform p;
    private int width, height;
    private int tab = lastTab;
    private List<Item> items = new ArrayList<Item>();
    private int firstRow;
    private boolean draggingSlider;
    private final List<Btn> buttons = new ArrayList<Btn>();
    private int px, py, pw, ph, listX, listY, listW, listH, rightX, rightW;

    // rename dialog
    private boolean renaming;
    private String renameFile = "";
    private final StringBuilder renameText = new StringBuilder();
    private int caret;
    private long caretStart;
    private final List<Btn> modalButtons = new ArrayList<Btn>();
    private int mx0, my0, mw, mh; // modal rect

    public PickerUi(Platform platform) {
        this.p = platform;
    }

    // ---------------------------------------------------------------- data

    static boolean supportedAnimation(Path f) {
        String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".gif") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp");
    }

    private void buildItems() {
        Config cfg = LivingInvClient.config;
        List<Item> out = new ArrayList<Item>();
        if (tab == 0) {
            try (Stream<Path> s = Files.list(LivingInvClient.dir)) {
                List<Path> files = s.filter(f -> Files.isRegularFile(f) && supportedAnimation(f)).sorted().collect(Collectors.<Path>toList());
                for (Path f : files) {
                    String name = f.getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    String ext = dot > 0 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "";
                    long kb = 0;
                    try { kb = Files.size(f) / 1024; } catch (IOException ignored) { }
                    String size = kb >= 1024 ? String.format(Locale.ROOT, "%.1f MB", kb / 1024.0) : kb + " KB";
                    String alias = cfg.names.get(name);
                    boolean named = alias != null && !alias.isEmpty();
                    String base = dot > 0 ? name.substring(0, dot) : name;
                    String detail = (named ? name + "  \u00b7  " : "") + ext + "  \u00b7  " + size;
                    out.add(new Item(name, named ? alias : base, detail, name.equals(cfg.selected)));
                }
            } catch (IOException ignored) { }
        } else {
            for (Overlays.Entry e : Overlays.list()) {
                String label = e.name != null ? e.name : p.tr("livinginv.default");
                out.add(new Item(e.id, label, p.tr(e.user ? "livinginv.source.user" : "livinginv.source.builtin"), e.id.equals(cfg.overlay)));
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
        buildButtons();
    }

    // ---------------------------------------------------------------- layout

    public void init(int screenW, int screenH) {
        width = screenW;
        height = screenH;
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
        buildItems();
        clampScroll();
        buildButtons();
    }

    private int visibleRows() { return Math.max(1, listH / ROW); }

    private void clampScroll() {
        int max = Math.max(0, items.size() - visibleRows());
        firstRow = Math.max(0, Math.min(firstRow, max));
    }

    private void buildButtons() {
        buttons.clear();
        final Config cfg = LivingInvClient.config;
        int by = py + ph - 28, bw = (pw - 20 - 8) / 3;
        buttons.add(new Btn(p.tr("livinginv.folder"), px + 10, by, bw, 20, true, false, new Runnable() {
            @Override public void run() { FolderOpener.open(tab == 0 ? LivingInvClient.dir : Overlays.userDir()); }
        }));
        boolean midEnabled = tab == 0 ? !cfg.selected.isEmpty() : !cfg.overlay.isEmpty();
        buttons.add(new Btn(p.tr(tab == 0 ? "livinginv.stop" : "livinginv.reset_overlay"), px + 10 + bw + 4, by, bw, 20, midEnabled, false, new Runnable() {
            @Override public void run() {
                if (tab == 0) { cfg.selected = ""; LivingInvClient.saveConfig(); PanelCore.reload(); }
                else { cfg.overlay = ""; LivingInvClient.saveConfig(); PanelCore.invalidateOverlay(); }
                buildItems();
                buildButtons();
            }
        }));
        buttons.add(new Btn(p.tr("livinginv.done"), px + 10 + (bw + 4) * 2, by, bw, 20, true, true, new Runnable() {
            @Override public void run() { p.close(); }
        }));
    }

    private void switchTab(int t) {
        if (t == tab) return;
        tab = t;
        lastTab = t;
        firstRow = 0;
        buildItems();
        buildButtons();
    }

    private void openRename(String file) {
        renaming = true;
        renameFile = file;
        String cur = LivingInvClient.config.names.get(file);
        renameText.setLength(0);
        if (cur != null) renameText.append(cur);
        caret = renameText.length();
        caretStart = System.currentTimeMillis();
        mw = Math.min(width - 20, 270);
        mh = 118;
        mx0 = (width - mw) / 2;
        my0 = (height - mh) / 2;
        modalButtons.clear();
        int bw = (mw - 20 - 6) / 2, by = my0 + mh - 28;
        modalButtons.add(new Btn(p.tr("livinginv.cancel"), mx0 + 10, by, bw, 20, true, false, new Runnable() {
            @Override public void run() { renaming = false; }
        }));
        modalButtons.add(new Btn(p.tr("livinginv.ok"), mx0 + 10 + bw + 6, by, bw, 20, true, true, new Runnable() {
            @Override public void run() { confirmRename(); }
        }));
    }

    private void confirmRename() {
        String t = renameText.toString().trim();
        if (t.isEmpty()) LivingInvClient.config.names.remove(renameFile);
        else LivingInvClient.config.names.put(renameFile, t);
        LivingInvClient.saveConfig();
        renaming = false;
        buildItems();
        buildButtons();
    }

    // ---------------------------------------------------------------- slider helpers

    private int sliderY() { return listY + listH - 24; }

    private boolean overSlider(double mx, double my) {
        return tab == 1 && mx >= rightX && mx <= rightX + rightW && my >= sliderY() + 8 && my <= sliderY() + 24;
    }

    private void setOpacityFromMouse(double mx) {
        int v = (int) Math.round((mx - rightX - 3) / (double) (rightW - 6) * 100);
        LivingInvClient.config.overlayOpacity = Math.max(0, Math.min(100, v));
    }

    // ---------------------------------------------------------------- input (called by the screen)

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (renaming) {
            for (Btn b : modalButtons) if (b.enabled && b.hit(mx, my)) { b.action.run(); return true; }
            return true; // modal swallows clicks
        }
        for (Btn b : buttons) if (b.enabled && b.hit(mx, my)) { b.action.run(); return true; }
        if (overSlider(mx, my)) { draggingSlider = true; setOpacityFromMouse(mx); return true; }
        for (int t = 0; t < 2; t++) {
            int tx = px + 10 + t * 96;
            if (mx >= tx && mx < tx + 92 && my >= py + 26 && my < py + 42) { switchTab(t); return true; }
        }
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int idx = firstRow + (int) ((my - listY) / ROW);
            if (idx >= 0 && idx < items.size() && (idx - firstRow) < visibleRows()) {
                Item it = items.get(idx);
                if (tab == 0 && mx >= listX + listW - 25) { openRename(it.id); return true; }
                select(it);
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my) {
        if (draggingSlider) { setOpacityFromMouse(mx); return true; }
        return false;
    }

    public boolean mouseReleased() {
        if (draggingSlider) { draggingSlider = false; LivingInvClient.saveConfig(); return true; }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double vertical) {
        if (renaming) return true;
        if (overSlider(mx, my)) {
            Config cfg = LivingInvClient.config;
            cfg.overlayOpacity = Math.max(0, Math.min(100, cfg.overlayOpacity + (int) Math.signum(vertical) * 5));
            LivingInvClient.saveConfig();
            return true;
        }
        if (mx >= listX && mx < listX + listW + 6 && my >= listY && my < listY + listH) {
            firstRow -= (int) Math.signum(vertical);
            clampScroll();
            return true;
        }
        return false;
    }

    public boolean charTyped(int codepoint) {
        if (!renaming || codepoint < 32 || codepoint == 127 || renameText.length() >= 40) return renaming;
        renameText.insert(caret, new String(Character.toChars(codepoint)));
        caret += Character.charCount(codepoint);
        caretStart = System.currentTimeMillis();
        return true;
    }

    /** @return true if the key was consumed (Escape closes only the dialog while it is open). */
    public boolean keyPressed(int key, int modifiers) {
        if (!renaming) return false;
        caretStart = System.currentTimeMillis();
        if (key == KEY_ESC) { renaming = false; return true; }
        if (key == KEY_ENTER || key == KEY_KP_ENTER) { confirmRename(); return true; }
        if (key == KEY_BACKSPACE) { if (caret > 0) { renameText.deleteCharAt(caret - 1); caret--; } return true; }
        if (key == KEY_DELETE) { if (caret < renameText.length()) renameText.deleteCharAt(caret); return true; }
        if (key == KEY_LEFT) { caret = Math.max(0, caret - 1); return true; }
        if (key == KEY_RIGHT) { caret = Math.min(renameText.length(), caret + 1); return true; }
        if (key == KEY_HOME) { caret = 0; return true; }
        if (key == KEY_END) { caret = renameText.length(); return true; }
        if (key == KEY_V && (modifiers & (2 | 8)) != 0) { // Ctrl+V / Cmd+V
            String clip = p.clipboard();
            if (clip != null) {
                for (int i = 0; i < clip.length() && renameText.length() < 40; i++) {
                    char c = clip.charAt(i);
                    if (c >= 32 && c != 127) { renameText.insert(caret, c); caret++; }
                }
            }
            return true;
        }
        return true; // swallow everything else while typing
    }

    public void filesDropped(List<Path> paths) {
        Path target = tab == 0 ? LivingInvClient.dir : Overlays.userDir();
        for (Path f : paths) {
            String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean ok = tab == 0 ? supportedAnimation(f) : n.endsWith(".png");
            if (!ok) continue;
            try {
                Files.createDirectories(target);
                Files.copy(f, target.resolve(f.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) { }
        }
        buildItems();
        clampScroll();
    }

    public void removed() {
        p.releasePreviews();
        LivingInvClient.saveConfig();
    }

    /** Test hooks. */
    public void debugTab(int t) { switchTab(t); }

    public void debugRename(int index, String text) {
        if (index < 0 || index >= items.size()) return;
        openRename(items.get(index).id);
        renameText.setLength(0);
        renameText.append(text);
        caret = renameText.length();
    }

    public void debugSelect(int index) { if (index >= 0 && index < items.size()) select(items.get(index)); }

    // ---------------------------------------------------------------- drawing helpers

    private void border(int x, int y, int w, int h, int c) {
        p.fill(x, y, x + w, y + 1, c);
        p.fill(x, y + h - 1, x + w, y + h, c);
        p.fill(x, y, x + 1, y + h, c);
        p.fill(x + w - 1, y, x + w, y + h, c);
    }

    private void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        int hgt = y2 - y1;
        for (int i = 0; i < hgt; i++) {
            float t = hgt <= 1 ? 0 : i / (float) (hgt - 1);
            int c = 0;
            for (int sh = 24; sh >= 0; sh -= 8) {
                int a = (top >>> sh) & 255, b = (bottom >>> sh) & 255;
                c |= ((int) (a + (b - a) * t) & 255) << sh;
            }
            p.fill(x1, y1 + i, x2, y1 + i + 1, c);
        }
    }

    private String trim(String s, int maxW) {
        if (p.textWidth(s) <= maxW) return s;
        int end = s.length();
        while (end > 0 && p.textWidth(s.substring(0, end) + "...") > maxW) end--;
        return s.substring(0, end) + "...";
    }

    private List<String> wrap(String s, int maxW) {
        List<String> lines = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (p.textWidth(test) <= maxW || cur.length() == 0) { cur.setLength(0); cur.append(test); }
            else { lines.add(cur.toString()); cur.setLength(0); cur.append(word); }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private void button(Btn b, int mx, int my) {
        boolean hot = b.enabled && b.hit(mx, my);
        int bg, bd, tx;
        if (!b.enabled) { bg = 0xFF141A21; bd = 0xFF222C37; tx = 0xFF5E6B78; }
        else if (b.primary) { bg = hot ? 0xFF2E8C84 : 0xFF22706A; bd = ACCENT; tx = 0xFFFFFFFF; }
        else { bg = hot ? 0xFF2A3A49 : 0xFF1C2630; bd = hot ? ACCENT : BORDER; tx = TEXT; }
        p.fill(b.x, b.y, b.x + b.w, b.y + b.h, bd);
        p.fill(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + b.h - 1, bg);
        if (b.enabled) p.fill(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + 2, 0x16FFFFFF);
        p.text(b.label, b.x + (b.w - p.textWidth(b.label)) / 2, b.y + (b.h - 8) / 2, tx);
    }

    // ---------------------------------------------------------------- drawing

    /** Draws the menu on top of the (already drawn) dimmed game background. */
    public void render(int mx, int my) {
        int hoverX = renaming ? -1000 : mx, hoverY = renaming ? -1000 : my;

        p.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, BORDER);
        p.fill(px, py, px + pw, py + ph, BG);
        gradient(px, py, px + pw, py + 44, HEAD_TOP, HEAD_BOTTOM);
        p.fill(px, py + 44, px + pw, py + 45, BORDER);
        p.text(p.tr("livinginv.title"), px + 12, py + 9, ACCENT);

        String[] keys = {"livinginv.tab.animations", "livinginv.tab.overlays"};
        for (int t = 0; t < 2; t++) {
            int tx = px + 10 + t * 96;
            boolean active = t == tab;
            boolean hot = hoverX >= tx && hoverX < tx + 92 && hoverY >= py + 26 && hoverY < py + 42;
            if (hot && !active) p.fill(tx, py + 26, tx + 92, py + 42, ROW_HOVER);
            String label = p.tr(keys[t]);
            p.text(label, tx + (92 - p.textWidth(label)) / 2, py + 30, active ? TEXT : DIM);
            if (active) p.fill(tx, py + 41, tx + 92, py + 44, ACCENT);
        }

        drawList(hoverX, hoverY);
        if (tab == 0) drawAnimationInfo(); else drawOverlayPane(hoverX, hoverY);
        for (Btn b : buttons) button(b, hoverX, hoverY);

        if (renaming) drawRenameDialog(mx, my);
    }

    private int hoveredIndex(int mx, int my) {
        if (mx < listX || mx >= listX + listW || my < listY || my >= listY + visibleRows() * ROW) return -1;
        int idx = firstRow + (my - listY) / ROW;
        return idx >= 0 && idx < items.size() ? idx : -1;
    }

    private void drawList(int mx, int my) {
        border(listX - 1, listY - 1, listW + 2, listH + 2, BORDER);
        p.fill(listX, listY, listX + listW, listY + listH, 0xFF0E131A);

        if (items.isEmpty()) {
            String msg = p.tr(tab == 0 ? "livinginv.empty.anim" : "livinginv.empty.overlay");
            p.text(msg, listX + (listW - p.textWidth(msg)) / 2, listY + listH / 2 - 4, DIM);
            return;
        }
        int hover = hoveredIndex(mx, my);
        int rows = Math.min(visibleRows(), items.size() - firstRow);
        for (int r = 0; r < rows; r++) {
            int i = firstRow + r, y = listY + r * ROW;
            Item it = items.get(i);
            if (it.selected) p.fill(listX, y, listX + listW, y + ROW, ROW_SELECTED);
            else if (i == hover) p.fill(listX, y, listX + listW, y + ROW, ROW_HOVER);
            if (it.selected) p.fill(listX, y + 2, listX + 3, y + ROW - 2, ACCENT);
            int tw = listW - 20 - (tab == 0 ? 16 : 0);
            p.text(trim(it.label, tw), listX + 9, y + 4, it.selected ? ACCENT : TEXT);
            p.text(trim(it.detail, tw), listX + 9, y + 14, DIM);
            p.fill(listX + 6, y + ROW - 1, listX + listW - 6, y + ROW, 0x14FFFFFF);
            if (tab == 0) {
                boolean hotPencil = i == hover && mx >= listX + listW - 25;
                drawPencil(listX + listW - 22, y + 7, hotPencil ? ACCENT : (i == hover ? TEXT : 0xFF5E6B78));
            }
        }
        if (items.size() > visibleRows()) {
            int vis = visibleRows(), total = items.size();
            int thumb = Math.max(16, listH * vis / total);
            int ty = listY + (int) ((listH - thumb) * (firstRow / (double) (total - vis)));
            p.fill(listX + listW - 4, listY + 2, listX + listW - 2, listY + listH - 2, 0x22FFFFFF);
            p.fill(listX + listW - 4, ty, listX + listW - 2, ty + thumb, ACCENT);
        }
    }

    private void drawPencil(int x, int y, int color) {
        for (int k = 0; k < 6; k++) p.fill(x + k, y + 7 - k, x + k + 2, y + 9 - k, color);
        p.fill(x + 6, y + 1, x + 9, y + 4, 0xFFE07A7A);
        p.fill(x, y + 8, x + 2, y + 10, 0xFFD6E2EC);
    }

    private void drawAnimationInfo() {
        Config cfg = LivingInvClient.config;
        int y = listY + 2;
        p.text(p.tr("livinginv.current"), rightX, y, DIM);
        y += 12;
        String alias = cfg.names.get(cfg.selected);
        boolean named = !cfg.selected.isEmpty() && alias != null && !alias.isEmpty();
        String name = cfg.selected.isEmpty() ? p.tr("livinginv.none") : (named ? alias : cfg.selected);
        p.text(trim(name, rightW), rightX, y, cfg.selected.isEmpty() ? DIM : TEXT);
        y += named ? 11 : 0;
        if (named) p.text(trim(cfg.selected, rightW), rightX, y, DIM);
        y += 18;

        if (PanelCore.progress >= 0) {
            p.fill(rightX, y, rightX + rightW, y + 8, 0xFF0E131A);
            p.fill(rightX, y, rightX + rightW * PanelCore.progress / 100, y + 8, ACCENT);
            border(rightX, y, rightW, 8, BORDER);
            y += 14;
            p.text(PanelCore.progress + "%", rightX, y, DIM);
            y += 14;
        }
        String st = PanelCore.status;
        if (!st.isEmpty()) {
            for (String l : wrap(st, rightW)) { p.text(l, rightX, y, WARN); y += 10; }
            y += 4;
        }
        for (String l : wrap(p.tr("livinginv.tip.anim"), rightW)) {
            if (y > listY + listH - 34) break;
            p.text(l, rightX, y, DIM);
            y += 10;
        }
        List<String> hint = wrap(p.tr("livinginv.drop.anim"), rightW);
        int hy = listY + listH - hint.size() * 10;
        for (String l : hint) { p.text(l, rightX, hy, DIM); hy += 10; }
    }

    private void drawOverlayPane(int mx, int my) {
        if (!items.isEmpty()) {
            int hover = hoveredIndex(mx, my);
            Item it = hover >= 0 ? items.get(hover) : null;
            if (it == null) for (Item i : items) if (i.selected) { it = i; break; }
            if (it == null) it = items.get(0);

            int availH = sliderY() - 20 - (listY + 2);
            float s = Math.min(rightW / (float) PanelComposer.PANEL_W, availH / (float) PanelComposer.PANEL_H);
            int dw = (int) (PanelComposer.PANEL_W * s), dh = (int) (PanelComposer.PANEL_H * s);
            int ox = rightX + (rightW - dw) / 2, oy = listY + 2;
            for (int cy = 0; cy < dh; cy += 6)
                for (int cx = 0; cx < dw; cx += 6) {
                    boolean a = ((cx / 6) + (cy / 6)) % 2 == 0;
                    p.fill(ox + cx, oy + cy, Math.min(ox + dw, ox + cx + 6), Math.min(oy + dh, oy + cy + 6), a ? 0xFF2A3440 : 0xFF222B35);
                }
            p.drawPreview(it.id, ox, oy, dw, dh, LivingInvClient.config.overlayOpacity);
            border(ox - 1, oy - 1, dw + 2, dh + 2, BORDER);
            String cap = trim(it.label, rightW);
            p.text(cap, rightX + (rightW - p.textWidth(cap)) / 2, oy + dh + 6, it.selected ? ACCENT : TEXT);
        }
        drawSlider(mx, my);
    }

    private void drawSlider(int mx, int my) {
        int op = LivingInvClient.config.overlayOpacity;
        int sy = sliderY();
        p.text(p.tr("livinginv.opacity"), rightX, sy, DIM);
        String val = op + "%";
        p.text(val, rightX + rightW - p.textWidth(val), sy, TEXT);
        int ty = sy + 13;
        p.fill(rightX, ty, rightX + rightW, ty + 6, BORDER);
        p.fill(rightX + 1, ty + 1, rightX + rightW - 1, ty + 5, 0xFF0E131A);
        p.fill(rightX + 1, ty + 1, rightX + 1 + (rightW - 2) * op / 100, ty + 5, ACCENT);
        int thumbX = rightX + (rightW - 6) * op / 100;
        boolean hot = draggingSlider || overSlider(mx, my);
        p.fill(thumbX, ty - 3, thumbX + 6, ty + 9, hot ? 0xFFFFFFFF : 0xFFC9D6E0);
    }

    private void drawRenameDialog(int mx, int my) {
        p.fill(0, 0, width, height, 0x88000000);
        p.fill(mx0 - 1, my0 - 1, mx0 + mw + 1, my0 + mh + 1, BORDER);
        p.fill(mx0, my0, mx0 + mw, my0 + mh, 0xFF121820); // fully opaque, or the list text shows through
        gradient(mx0, my0, mx0 + mw, my0 + 30, HEAD_TOP, HEAD_BOTTOM);
        p.fill(mx0, my0 + 30, mx0 + mw, my0 + 31, BORDER);
        p.text(p.tr("livinginv.rename.title"), mx0 + 12, my0 + 11, ACCENT);
        p.text(trim(renameFile, mw - 24), mx0 + 12, my0 + 36, DIM);

        int bx = mx0 + 12, by = my0 + 46, bw = mw - 24, bh = 20;
        p.fill(bx, by, bx + bw, by + bh, ACCENT);
        p.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, 0xFF0E131A);

        String full = renameText.toString();
        int inner = bw - 12;
        int start = 0;
        while (start < caret && p.textWidth(full.substring(start, caret)) > inner - 2) start++;
        String shown = full.substring(start);
        while (shown.length() > 0 && p.textWidth(shown) > inner) shown = shown.substring(0, shown.length() - 1);
        if (full.isEmpty()) {
            int dot = renameFile.lastIndexOf('.');
            p.text(trim(dot > 0 ? renameFile.substring(0, dot) : renameFile, inner), bx + 6, by + 6, 0xFF4A5663);
        } else {
            p.text(shown, bx + 6, by + 6, TEXT);
        }
        if (((System.currentTimeMillis() - caretStart) / 500) % 2 == 0) {
            int cx = bx + 6 + p.textWidth(full.substring(start, caret));
            p.fill(cx, by + 4, cx + 1, by + 15, 0xFFFFFFFF);
        }
        p.text(p.tr("livinginv.rename.hint"), mx0 + 12, my0 + 72, DIM);
        for (Btn b : modalButtons) button(b, mx, my);
    }
}
