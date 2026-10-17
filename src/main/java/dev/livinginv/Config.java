package dev.livinginv;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    /** File name inside config/livinginv/ ("" = disabled). */
    public String selected = "";
    /** Black overlay strength, 0..100. */
    public int dim = 30;
    /** Hard cap on animation length. Default is 10 minutes. Real limit is the memory budget below. */
    public int maxDurationSeconds = 600;
    /** RAM used for decoded frames. Long GIFs automatically get a lower resolution to fit. */
    public int memoryBudgetMb = 384;
    /** Max size (longest side, px) a frame is stored at. */
    public int maxResolution = 640;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Config load(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                Config c = GSON.fromJson(Files.readString(file), Config.class);
                if (c != null) { c.sanitize(); return c; }
            }
        } catch (Exception ignored) { }
        Config c = new Config();
        c.save(file);
        return c;
    }

    public void save(Path file) {
        try { Files.writeString(file, GSON.toJson(this)); } catch (IOException ignored) { }
    }

    private void sanitize() {
        if (selected == null) selected = "";
        dim = Math.max(0, Math.min(100, dim));
        maxDurationSeconds = Math.max(1, maxDurationSeconds);
        memoryBudgetMb = Math.max(32, memoryBudgetMb);
        maxResolution = Math.max(GifDecoder.MIN_SIDE, maxResolution);
    }
}
