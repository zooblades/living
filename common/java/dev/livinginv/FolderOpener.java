package dev.livinginv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Opens a folder in the system file manager without relying on any Minecraft API. */
public final class FolderOpener {
    private FolderOpener() { }

    public static void open(Path dir) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String p = dir.toAbsolutePath().toString();
        try {
            if (os.contains("win")) new ProcessBuilder("explorer", p).start();
            else if (os.contains("mac")) new ProcessBuilder("open", p).start();
            else new ProcessBuilder("xdg-open", p).start();
        } catch (IOException ignored) { }
    }
}
