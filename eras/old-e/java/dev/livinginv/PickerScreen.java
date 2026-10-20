package dev.livinginv;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PickerScreen extends Screen {
    private static final int PER_PAGE = 6;
    private final Screen parent;
    private List<Path> files = new ArrayList<Path>();
    private int page;

    public PickerScreen(Screen parent) {
        super(Compat.translatable("livinginv.title"));
        this.parent = parent;
    }

    static boolean supported(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".gif") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp");
    }

    private void scan() {
        try (Stream<Path> s = Files.list(LivingInvClient.dir)) {
            files = s.filter(Files::isRegularFile).filter(PickerScreen::supported).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            files = Collections.emptyList();
        }
    }

    private void refresh() {
        this.init(this.client, this.width, this.height);
    }

    @Override
    protected void init() {
        scan();
        final Config cfg = LivingInvClient.config;
        int w = 220, x = width / 2 - w / 2, y = 34;
        int pages = Math.max(1, (files.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));

        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= files.size()) break;
            final String name = files.get(idx).getFileName().toString();
            String label = (name.equals(cfg.selected) ? "\u2714 " : "") + (name.length() > 30 ? name.substring(0, 27) + "..." : name);
            addButton(Compat.button(x, y + i * 24, w, 20, Compat.literal(label), () -> {
                cfg.selected = name;
                LivingInvClient.saveConfig();
                PanelCore.reload();
                refresh();
            }));
        }

        int ry = y + PER_PAGE * 24 + 4;
        ButtonWidget prev = addButton(Compat.button(x, ry, 20, 20, Compat.literal("<"), () -> { page--; refresh(); }));
        prev.active = page > 0;
        ButtonWidget next = addButton(Compat.button(x + w - 20, ry, 20, 20, Compat.literal(">"), () -> { page++; refresh(); }));
        next.active = page < pages - 1;

        ry += 24;
        addButton(Compat.button(x, ry, w / 2 - 2, 20, Compat.translatable("livinginv.off"), () -> {
            cfg.selected = "";
            LivingInvClient.saveConfig();
            PanelCore.reload();
            refresh();
        }));
        addButton(Compat.button(x + w / 2 + 2, ry, w / 2 - 2, 20, Compat.translatable("livinginv.folder"), () -> FolderOpener.open(LivingInvClient.dir)));

        ry += 24;
        addButton(Compat.button(x, ry, w, 20, Compat.translatable("livinginv.done"), () -> leave()));
    }

    private void centered(MatrixStack m, Text t, int y, int color) {
        textRenderer.drawWithShadow(m, t, (width - textRenderer.getWidth(t)) / 2f, (float) y, color);
    }

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        renderBackground(m);
        super.render(m, mouseX, mouseY, delta);
        centered(m, title, 14, 0xFFFFFFFF);
        centered(m, Compat.translatable("livinginv.drop"), height - 22, 0xFFAAAAAA);
        String st = PanelCore.status;
        if (PanelCore.progress >= 0) st = "Loading " + PanelCore.progress + "%";
        if (!st.isEmpty()) centered(m, Compat.literal(st), height - 10, 0xFFFFDD55);
    }

    @Override
    public void filesDragged(List<Path> paths) {
        for (Path p : paths) {
            if (!supported(p)) continue;
            try {
                Files.copy(p, LivingInvClient.dir.resolve(p.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) { }
        }
        refresh();
    }

    // Screen declares either onClose() or close() depending on the Minecraft version; the other is a spare method.
    public void onClose() { leave(); }

    public void close() { leave(); }

    private void leave() {
        Compat.show(client, parent);
    }
}
