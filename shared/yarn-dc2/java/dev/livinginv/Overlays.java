package dev.livinginv;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Finds and opens overlay presets: built-in / resource-pack ones and your own files. */
public final class Overlays {
    private static final Identifier DEFAULT = Identifier.tryParse("livinginv:textures/gui/inventory_overlay.png");

    public static final class Entry {
        public final String id, name;
        public final boolean user;
        Entry(String id, String name, boolean user) { this.id = id; this.name = name; this.user = user; }
    }

    private Overlays() { }

    public static Path userDir() {
        return LivingInvClient.dir.resolve("overlays");
    }

    public static List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        out.add(new Entry("", null, false)); // default, name comes from the language file
        Map<Identifier, Resource> found = MinecraftClient.getInstance().getResourceManager()
                .findResources("textures/gui/overlays", id -> id.getPath().endsWith(".png"));
        List<Identifier> ids = new ArrayList<>(found.keySet());
        ids.sort(Comparator.comparing(Identifier::toString));
        for (Identifier id : ids) out.add(new Entry(id.toString(), pretty(id.getPath()), false));
        try (Stream<Path> s = Files.list(userDir())) {
            List<Path> files = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted().collect(Collectors.toList());
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
        Identifier rid = id.isEmpty() ? DEFAULT : Identifier.tryParse(id);
        if (rid == null) throw new IOException("bad id " + id);
        return MinecraftClient.getInstance().getResourceManager().getResource(rid)
                .orElseThrow(() -> new IOException("overlay missing: " + id)).getInputStream();
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
