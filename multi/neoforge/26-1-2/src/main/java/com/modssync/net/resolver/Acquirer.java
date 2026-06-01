package com.modssync.net.resolver;

import com.modssync.model.ServerMod;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs each mod through the ordered sources, taking the first success. */
public final class Acquirer {

    public record Result(List<Path> downloaded, List<ServerMod> unresolved) {}

    private final List<ModSource> sources;

    public Acquirer(List<ModSource> sources) {
        this.sources = sources;
    }

    public Result acquire(List<ServerMod> mods, Path destDir) {
        List<Path> downloaded = new ArrayList<>();
        List<ServerMod> unresolved = new ArrayList<>();
        for (ServerMod mod : mods) {
            Path file = null;
            for (ModSource source : sources) {
                file = source.download(mod, destDir);
                if (file != null) {
                    break;
                }
            }
            if (file != null) {
                downloaded.add(file);
            } else {
                unresolved.add(mod);
            }
        }
        return new Result(downloaded, unresolved);
    }
}
