package dev.livinginv;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

public class PickerScreen extends Screen {
    private static final int PER_PAGE = 6;
    private final Screen parent;
    private List<Path> files = List.of();
    private int page;

    public PickerScreen(Screen parent) {
        super(Component.translatable("livinginv.title"));
        this.parent = parent;
    }

    static boolean supported(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".gif") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp");
    }

    private void scan() {
        try (var s = Files.list(LivingInvClient.dir)) {
            files = s.filter(Files::isRegularFile).filter(PickerScreen::supported).sorted().toList();
        } catch (IOException e) {
            files = List.of();
        }
    }

    @Override
    protected void init() {
        scan();
        Config cfg = LivingInvClient.config;
        int w = 220, x = width / 2 - w / 2, y = 34;
        int pages = Math.max(1, (files.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));

        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= files.size()) break;
            String name = files.get(idx).getFileName().toString();
            String label = (name.equals(cfg.selected) ? "\u2714 " : "") + (name.length() > 30 ? name.substring(0, 27) + "..." : name);
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                cfg.selected = name;
                LivingInvClient.saveConfig();
                Background.reload();
                rebuildWidgets();
            }).bounds(x, y + i * 24, w, 20).build());
        }

        int ry = y + PER_PAGE * 24 + 4;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); })
                .bounds(x, ry, 20, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); })
                .bounds(x + w - 20, ry, 20, 20).build()).active = page < pages - 1;

        ry += 24;
        addRenderableWidget(Button.builder(Component.translatable("livinginv.off"), b -> {
            cfg.selected = "";
            LivingInvClient.saveConfig();
            Background.reload();
            rebuildWidgets();
        }).bounds(x, ry, w / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("livinginv.slots", cfg.slotOpacity), b -> {
            cfg.slotOpacity = (cfg.slotOpacity + 10) % 110;
            LivingInvClient.saveConfig();
            rebuildWidgets();
        }).bounds(x + w / 2 + 2, ry, w / 2 - 2, 20).build());

        ry += 24;
        addRenderableWidget(Button.builder(Component.translatable("livinginv.folder"),
                b -> FolderOpener.open(LivingInvClient.dir))
                .bounds(x, ry, w / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("livinginv.done"), b -> onClose())
                .bounds(x + w / 2 + 2, ry, w / 2 - 2, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
        g.centeredText(font, Component.translatable("livinginv.drop"), width / 2, height - 22, 0xFFAAAAAA);
        String st = Background.status;
        if (Background.progress >= 0) st = "Loading " + Background.progress + "%";
        if (!st.isEmpty()) g.centeredText(font, st, width / 2, height - 10, 0xFFFFDD55);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        for (Path p : paths) {
            if (!supported(p)) continue;
            try {
                Files.copy(p, LivingInvClient.dir.resolve(p.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) { }
        }
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
