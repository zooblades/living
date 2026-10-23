package dev.livinginv.e2e;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import dev.livinginv.PickerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import dev.livinginv.LivingInvClient;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Opens the mod menu from the title screen and screenshots it while xdotool clicks it from outside. */
public class Driver implements ClientModInitializer {
    private int tick, phase, phaseStart;
    private volatile boolean done;
    private Object current, title;
    private PickerScreen picker;

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            current = screen;
            if (screen instanceof TitleScreen) title = screen;
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(240_000L); } catch (InterruptedException ignored) { }
            if (done) return;
            System.out.println("[e2e] HANG detected in phase " + phase + " (tick " + tick + ")");
            for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                if (!e.getKey().getName().contains("Render")) continue;
                System.out.println("[e2e] THREAD " + e.getKey().getName());
                for (StackTraceElement el : e.getValue()) System.out.println("[e2e]    at " + el);
            }
            Runtime.getRuntime().halt(3);
        }, "e2e-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void go(int next) { System.out.println("[e2e] phase " + phase + " -> " + next + " at tick " + tick); phase = next; phaseStart = tick; }

    private void demoFiles() throws Exception {
        String[] names = {"ocean", "sunset", "northern lights"};
        float[] hues = {0.58f, 0.04f, 0.38f};
        for (int n = 0; n < names.length; n++) {
            BufferedImage img = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 180; y++)
                for (int x = 0; x < 320; x++)
                    img.setRGB(x, y, Color.HSBtoRGB(hues[n] + y / 900f, 0.7f, 0.35f + 0.6f * x / 320f));
            ImageIO.write(img, "png", LivingInvClient.dir.resolve(names[n] + ".png").toFile());
        }
    }

    private void onTick(MinecraftClient mc) {
        tick++;
        try {
            int t = tick - phaseStart;
            switch (phase) {
                case 0:
                    if (title != null && current == title && t > 100) {
                        demoFiles();
                        picker = new PickerScreen((Screen) title);
                        mc.setScreen(picker);
                        System.out.println("[e2e] menu opened");
                        go(1);
                    }
                    break;
                case 1: if (t == 40) { shot(mc, "a-before-clicks"); go(2); } break;
                case 2: if (t == 120) { shot(mc, "b-after-overlays-tab-click"); go(3); } break;
                case 3: if (t == 240) { shot(mc, "c-after-pencil-click"); go(4); } break;
                case 4:
                    if (t == 160) {
                        boolean closed = current == title;
                        System.out.println("[e2e] menu closed by the Done click: " + closed);
                        shot(mc, "d-after-done-click");
                        go(5);
                    }
                    break;
                case 5:
                    if (t == 30) { System.out.println("[e2e] DONE"); done = true; mc.scheduleStop(); go(6); }
                    break;
                default:
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            System.out.println("[e2e] FAILED in phase " + phase + ": " + ex);
            Runtime.getRuntime().halt(4);
        }
    }

    private static void shot(MinecraftClient mc, String name) throws Exception {
        Path dir = mc.runDirectory.toPath().resolve("e2e-shots");
        Files.createDirectories(dir);
        ScreenshotRecorder.takeScreenshot(mc.getFramebuffer(), image -> {
            try (NativeImage img = image) {
                img.writeTo(dir.resolve(name + ".png"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        System.out.println("[e2e] screenshot " + name);
    }
}
