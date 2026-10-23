#!/usr/bin/env python3
"""Generates tests/menu-* : a version project + a Driver mod that opens the mod menu from the title screen,
screenshots it, and (with xdotool in CI) is clicked by real mouse/keyboard input."""
import os, re

def rd(p): return open(p).read()
def wr(p, s):
    os.makedirs(os.path.dirname(p), exist_ok=True); open(p, 'w').write(s)

DRIVER = '''package dev.livinginv.e2e;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
@@IMPORTS@@
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

    private void onTick(@@CLIENT@@ mc) {
        tick++;
        try {
            int t = tick - phaseStart;
            switch (phase) {
                case 0:
                    if (title != null && current == title && t > 100) {
                        demoFiles();
                        picker = new PickerScreen((@@SCREEN@@) title);
                        @@OPEN@@
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
                    if (t == 30) { System.out.println("[e2e] DONE"); done = true; @@STOP@@ go(6); }
                    break;
                default:
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            System.out.println("[e2e] FAILED in phase " + phase + ": " + ex);
            Runtime.getRuntime().halt(4);
        }
    }

    private static void shot(@@CLIENT@@ mc, String name) throws Exception {
        @@SHOT@@
        System.out.println("[e2e] screenshot " + name);
    }
}
'''
YARN = dict(
    IMPORTS='''import dev.livinginv.PickerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;''',
    CLIENT='MinecraftClient', SCREEN='Screen', OPEN='mc.setScreen(picker);', STOP='mc.scheduleStop();',
    SHOT='''Path dir = mc.runDirectory.toPath().resolve("e2e-shots");
        Files.createDirectories(dir);
        ScreenshotRecorder.takeScreenshot(mc.getFramebuffer(), image -> {
            try (NativeImage img = image) {
                img.writeTo(dir.resolve(name + ".png"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });''')
MOJ = dict(
    IMPORTS='''import dev.livinginv.PickerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;''',
    CLIENT='Minecraft', SCREEN='Screen', OPEN='mc.setScreenAndShow(picker);', STOP='mc.stop();',
    SHOT='Screenshot.grab(mc, false); // saved to screenshots/ with a timestamp; order = the markers in the log')

PROJECTS = [('menu-1218', '1.21.6-1.21.8', YARN), ('menu-yarn', '1.21.9-1.21.11', YARN), ('menu-mojmap', '26.3', MOJ)]

def main(root):
    for name, grp, api in PROJECTS:
        d = f'{root}/tests/{name}'
        wr(f'{d}/settings.gradle', rd(f'{root}/versions/{grp}/settings.gradle'))
        b = rd(f'{root}/versions/{grp}/build.gradle').replace("tasks.withType(JavaCompile)", "processResources.duplicatesStrategy = DuplicatesStrategy.EXCLUDE\n\ntasks.withType(JavaCompile)", 1)
        wr(f'{d}/build.gradle', b)
        g = rd(f'{root}/versions/{grp}/gradle.properties')
        g = re.sub(r'src_dirs=(.*)', lambda m: 'src_dirs=' + m.group(1) + ',src/driver/java', g)
        g = re.sub(r'mc_label=.*', 'mc_label=menu', g)
        wr(f'{d}/gradle.properties', g)
        wr(f'{d}/src/main/resources/fabric.mod.json', '''{
  "schemaVersion": 1,
  "id": "livinginv",
  "version": "${version}",
  "name": "Living Inventory (menu e2e)",
  "environment": "client",
  "entrypoints": { "client": ["dev.livinginv.LivingInvClient", "dev.livinginv.e2e.Driver"] },
  "mixins": ["livinginv.mixins.json"],
  "depends": { "fabricloader": ">=0.14.0", "minecraft": "${mc_depends}", "java": ">=${java_version}" }
}
''')
        s = DRIVER
        for k, v in api.items(): s = s.replace('@@' + k + '@@', v)
        wr(f'{d}/src/driver/java/dev/livinginv/e2e/Driver.java', s)

if __name__ == '__main__':
    main(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
