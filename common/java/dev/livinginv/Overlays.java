package dev.livinginv;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Overlay presets: the built-in default, other built-in / resource-pack overlays
 * (assets/&lt;ns&gt;/textures/gui/overlays/*.png) and your own files in config/livinginv/overlays.
 * The version-specific part (resource manager access) is the ResourceSource set by each build.
 */
public final class Overlays {
    public static final String DEFAULT_RESOURCE = "livinginv:textures/gui/inventory_overlay.png";

    /** Implemented once per Minecraft API generation. */
    public interface ResourceSource {
        /** Resource ids ("namespace:path") of all built-in / resource-pack overlay PNGs. */
        List<String> findBuiltin();

        InputStream open(String resourceId) throws Exception;
    }

    public static final class Entry {
        public final String id, name;
        public final boolean user;
        Entry(String id, String name, boolean user) { this.id = id; this.name = name; this.user = user; }
    }

    public static ResourceSource source;

    private Overlays() { }

    public static Path userDir() {
        return LivingInvClient.dir.resolve("overlays");
    }

    public static List<Entry> list() {
        List<Entry> out = new ArrayList<Entry>();
        out.add(new Entry("", null, false)); // default; its name comes from the language file
        if (source != null) {
            List<String> ids = new ArrayList<String>(source.findBuiltin());
            Collections.sort(ids);
            for (String id : ids) {
                if (!id.equals(DEFAULT_RESOURCE)) out.add(new Entry(id, pretty(id.substring(id.indexOf(':') + 1)), false));
            }
        }
        try (Stream<Path> s = Files.list(userDir())) {
            List<Path> files = s.filter(new java.util.function.Predicate<Path>() {
                @Override public boolean test(Path p) {
                    return Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png");
                }
            }).sorted().collect(Collectors.<Path>toList());
            for (Path f : files) out.add(new Entry("file:" + f.getFileName(), pretty(f.getFileName().toString()), true));
        } catch (IOException ignored) { }
        return out;
    }

    static String pretty(String path) {
        String n = path.substring(path.lastIndexOf('/') + 1);
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        n = n.replace('_', ' ').replace('-', ' ').trim();
        return n.isEmpty() ? path : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Opens the overlay with this id; throws if it does not exist. */
    public static InputStream open(String id) throws Exception {
        if (id.startsWith("file:")) {
            String name = id.substring(5);
            if (name.contains("/") || name.contains("\\") || name.contains("..")) throw new IOException("bad name");
            return Files.newInputStream(userDir().resolve(name));
        }
        return source.open(id.isEmpty() ? DEFAULT_RESOURCE : id);
    }

    /** Like open(), but falls back to the built-in default if the chosen overlay is gone. */
    public static InputStream openWithFallback(String id) throws Exception {
        try {
            return open(id);
        } catch (Exception e) {
            return open("");
        }
    }
}
