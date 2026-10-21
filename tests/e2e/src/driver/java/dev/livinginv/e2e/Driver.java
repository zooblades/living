package dev.livinginv.e2e;

import dev.livinginv.Overlays;
import dev.livinginv.PanelCore;
import dev.livinginv.PickerScreen;
import dev.livinginv.LivingInvClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.TranslatableTextContent;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Scripted end-to-end run: create a world, open the inventory, screenshot vanilla and modded. */
public class Driver implements ClientModInitializer {
    private int tick, phase, phaseStart;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(12 * 60 * 1000L); } catch (InterruptedException ignored) { }
            System.out.println("[e2e] WATCHDOG timeout in phase " + phase);
            Runtime.getRuntime().halt(3);
        }, "e2e-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void go(int next) {
        System.out.println("[e2e] phase " + phase + " -> " + next + " at tick " + tick);
        phase = next;
        phaseStart = tick;
    }

    private int inPhase() { return tick - phaseStart; }

    private void onTick(MinecraftClient mc) {
        tick++;
        try {
            step(mc);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("[e2e] FAILED in phase " + phase + ": " + t);
            Runtime.getRuntime().halt(4);
        }
    }

    private void step(MinecraftClient mc) throws Exception {
        Screen s = mc.currentScreen;
        switch (phase) {
            case 0: // wait for title screen, open "create world"
                if (s instanceof TitleScreen && inPhase() > 60) {
                    CreateWorldScreen.create(mc, mc.currentScreen);
                    go(1);
                }
                break;
            case 1: // press the "Create New World" button
                if (s instanceof CreateWorldScreen && inPhase() > 40) {
                    ButtonWidget create = findButton(s, "selectWorld.create");
                    if (create == null) throw new IllegalStateException("create button not found");
                    create.onPress();
                    go(2);
                }
                break;
            case 2: // wait for the world
                if (mc.player != null && mc.world != null && s == null && inPhase() > 200) {
                    mc.setScreen(new InventoryScreen(mc.player));
                    go(3);
                }
                break;
            case 3:
                if (inPhase() == 30) {
                    shot(mc, "1-vanilla");
                    writeTestImage();
                    LivingInvClient.config.selected = "test.png";
                    PanelCore.reload();
                    go(4);
                }
                break;
            case 4:
                if (inPhase() == 120) {
                    shot(mc, "2-modded");
                    makeDemoFiles();
                    mc.setScreen(new PickerScreen(mc.currentScreen));
                    go(5);
                }
                break;
            case 5:
                if (inPhase() == 15) {
                    shot(mc, "3-menu-animations");
                    ((PickerScreen) mc.currentScreen).debugTab(1);
                    go(6);
                }
                break;
            case 6:
                if (inPhase() == 15) {
                    shot(mc, "4-menu-overlays");
                    ((PickerScreen) mc.currentScreen).debugSelect(1); // pick a different overlay
                    go(7);
                }
                break;
            case 7:
                if (inPhase() == 15) {
                    shot(mc, "5-menu-overlay-selected");
                    LivingInvClient.config.overlayOpacity = 55;
                    ((PickerScreen) mc.currentScreen).debugTab(0);
                    ((PickerScreen) mc.currentScreen).debugRename(0, "Sunset glow");
                    go(8);
                }
                break;
            case 8:
                if (inPhase() == 10) {
                    shot(mc, "6-rename-dialog");
                    mc.currentScreen.keyPressed(257, 0, 0); // Enter confirms
                    go(9);
                }
                break;
            case 9:
                if (inPhase() == 10) {
                    shot(mc, "7-menu-renamed");
                    mc.setScreen(new InventoryScreen(mc.player));
                    go(10);
                }
                break;
            case 10:
                if (inPhase() == 60) {
                    shot(mc, "8-inventory-opacity-55");
                    System.out.println("[e2e] DONE");
                    mc.scheduleStop();
                    go(11);
                }
                break;
            default:
        }
    }

    private static ButtonWidget findButton(Screen screen, String translationKey) {
        for (Element e : screen.children()) {
            ButtonWidget b = find(e, translationKey);
            if (b != null) return b;
        }
        return null;
    }

    private static ButtonWidget find(Object e, String key) {
        if (e instanceof ButtonWidget) {
            ButtonWidget b = (ButtonWidget) e;
            if (b.getMessage().getContent() instanceof TranslatableTextContent
                    && ((TranslatableTextContent) b.getMessage().getContent()).getKey().equals(key)) return b;
        }
        if (e instanceof net.minecraft.client.gui.ParentElement) {
            for (Element c : ((net.minecraft.client.gui.ParentElement) e).children()) {
                ButtonWidget b = find(c, key);
                if (b != null) return b;
            }
        }
        return null;
    }

    private static void shot(MinecraftClient mc, String name) throws Exception {
        Path dir = mc.runDirectory.toPath().resolve("e2e-shots");
        Files.createDirectories(dir);
        try (NativeImage img = ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())) {
            img.writeTo(dir.resolve(name + ".png"));
        }
        System.out.println("[e2e] screenshot " + name);
    }

    /** Extra animations and two overlay variants derived from the default one, to fill the menu lists. */
    private static void makeDemoFiles() throws Exception {
        String[] names = {"ocean", "sunset", "northern lights", "city rain", "forest"};
        float[] hues = {0.58f, 0.04f, 0.38f, 0.65f, 0.30f};
        for (int n = 0; n < names.length; n++) {
            BufferedImage img = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 180; y++)
                for (int x = 0; x < 320; x++)
                    img.setRGB(x, y, Color.HSBtoRGB(hues[n] + y / 900f, 0.7f, 0.35f + 0.6f * x / 320f));
            ImageIO.write(img, "png", LivingInvClient.dir.resolve(names[n] + ".png").toFile());
        }
        BufferedImage base;
        try (java.io.InputStream in = Overlays.open("")) { base = ImageIO.read(in); }
        BufferedImage night = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        BufferedImage frost = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < base.getHeight(); y++)
            for (int x = 0; x < base.getWidth(); x++) {
                int c = base.getRGB(x, y), a = c >>> 24;
                boolean plate = a > 80 && a < 120 && (c & 0xFFFFFF) == 0;
                night.setRGB(x, y, plate ? (190 << 24) : c);
                frost.setRGB(x, y, plate ? (70 << 24 | 0xFFFFFF) : c);
            }
        java.nio.file.Files.createDirectories(Overlays.userDir());
        ImageIO.write(night, "png", Overlays.userDir().resolve("night.png").toFile());
        ImageIO.write(frost, "png", Overlays.userDir().resolve("frost_glass.png").toFile());
    }

    private static void writeTestImage() throws Exception {
        BufferedImage img = new BufferedImage(400, 225, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 225; y++)
            for (int x = 0; x < 400; x++)
                img.setRGB(x, y, Color.HSBtoRGB((x + y) / 625f, 0.75f, 0.95f));
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(6));
        g.drawOval(150, 60, 100, 100);
        g.dispose();
        ImageIO.write(img, "png", LivingInvClient.dir.resolve("test.png").toFile());
    }
}
