package dev.livinginv;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Finds / opens overlay textures through the game's resource manager (old API, up to 1.18.2). */
final class ResourceSourceImpl implements Overlays.ResourceSource {
    @Override
    public List<String> findBuiltin() {
        Collection<Identifier> found = MinecraftClient.getInstance().getResourceManager()
                .findResources("textures/gui/overlays", path -> path.endsWith(".png"));
        List<String> out = new ArrayList<String>();
        for (Identifier id : found) out.add(id.toString());
        return out;
    }

    @Override
    public InputStream open(String id) throws Exception {
        Identifier rid = Identifier.tryParse(id);
        if (rid == null) throw new IOException("bad id " + id);
        return MinecraftClient.getInstance().getResourceManager().getResource(rid).getInputStream();
    }
}
