package dev.livinginv;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Finds / opens overlay textures through the game's resource manager (mod assets and resource packs). */
final class ResourceSourceImpl implements Overlays.ResourceSource {
    @Override
    public List<String> findBuiltin() {
        Map<Identifier, Resource> found = Minecraft.getInstance().getResourceManager()
                .listResources("textures/gui/overlays", id -> id.getPath().endsWith(".png"));
        List<String> out = new ArrayList<String>();
        for (Identifier id : found.keySet()) out.add(id.toString());
        return out;
    }

    @Override
    public InputStream open(String id) throws Exception {
        Identifier rid = Identifier.parse(id);
        return Minecraft.getInstance().getResourceManager().getResource(rid)
                .orElseThrow(() -> new IOException("overlay missing: " + id)).open();
    }
}
