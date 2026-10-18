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
            addDrawableChild(Compat.button(x, y + i * 24, w, 20, Compat.literal(label), () -> {
                cfg.selected = name;
                LivingInvClient.saveConfig();
                Background.reload();
                refresh();
            }));
        }

        int ry = y + PER_PAGE * 24 + 4;
        ButtonWidget prev = addDrawableChild(Compat.button(x, ry, 20, 20, Compat.literal("<"), () -> { page--; refresh(); }));
        prev.active = page > 0;
        ButtonWidget next = addDrawableChild(Compat.button(x + w - 20, ry, 20, 20, Compat.literal(">"), () -> { page++; refresh(); }));
        next.active = page < pages - 1;

        ry += 24;
        addDrawableChild(Compat.button(x, ry, w / 2 - 2, 20, Compat.translatable("livinginv.off"), () -> {
            cfg.selected = "";
            LivingInvClient.saveConfig();
            Background.reload();
            refresh();
        }));
        addDrawableChild(Compat.button(x + w / 2 + 2, ry, w / 2 - 2, 20, Compat.translatable("livinginv.slots", cfg.slotOpacity), () -> {
            cfg.slotOpacity = (cfg.slotOpacity + 10) % 110;
            LivingInvClient.saveConfig();
            refresh();
        }));

        ry += 24;
        addDrawableChild(Compat.button(x, ry, w / 2 - 2, 20, Compat.translatable("livinginv.folder"), () -> FolderOpener.open(LivingInvClient.dir)));
        addDrawableChild(Compat.button(x + w / 2 + 2, ry, w / 2 - 2, 20, Compat.translatable("livinginv.done"), () -> close()));
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
        String st = Background.status;
        if (Background.progress >= 0) st = "Loading " + Background.progress + "%";
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

    @Override
    public void close() {
        Compat.show(client, parent);
    }
}
