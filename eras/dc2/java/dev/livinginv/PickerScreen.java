package dev.livinginv;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

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
        super(Text.translatable("livinginv.title"));
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
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                cfg.selected = name;
                LivingInvClient.saveConfig();
                Background.reload();
                clearAndInit();
            }).dimensions(x, y + i * 24, w, 20).build());
        }

        int ry = y + PER_PAGE * 24 + 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page--; clearAndInit(); })
                .dimensions(x, ry, 20, 20).build()).active = page > 0;
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page++; clearAndInit(); })
                .dimensions(x + w - 20, ry, 20, 20).build()).active = page < pages - 1;

        ry += 24;
        addDrawableChild(ButtonWidget.builder(Text.translatable("livinginv.off"), b -> {
            cfg.selected = "";
            LivingInvClient.saveConfig();
            Background.reload();
            clearAndInit();
        }).dimensions(x, ry, w / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("livinginv.folder"),
                b -> FolderOpener.open(LivingInvClient.dir))
                .dimensions(x + w / 2 + 2, ry, w / 2 - 2, 20).build());

        ry += 24;
        addDrawableChild(ButtonWidget.builder(Text.translatable("livinginv.done"), b -> close())
                .dimensions(x, ry, w, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("livinginv.drop"), width / 2, height - 22, 0xFFAAAAAA);
        String st = Background.status;
        if (Background.progress >= 0) st = "Loading " + Background.progress + "%";
        if (!st.isEmpty()) ctx.drawCenteredTextWithShadow(textRenderer, st, width / 2, height - 10, 0xFFFFDD55);
    }

    // Screen declares one of these two names depending on the Minecraft version; the other one is just a spare method.
    public void onFilesDropped(List<Path> paths) { handleDrop(paths); }

    public void filesDragged(List<Path> paths) { handleDrop(paths); }

    private void handleDrop(List<Path> paths) {
        for (Path p : paths) {
            if (!supported(p)) continue;
            try {
                Files.copy(p, LivingInvClient.dir.resolve(p.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) { }
        }
        clearAndInit();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
