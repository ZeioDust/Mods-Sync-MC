package com.modssync.net.resolver;

import com.modssync.model.ServerMod;
import java.nio.file.Path;

/** A strategy that tries to download one mod into destDir, returning the file or null. */
@FunctionalInterface
public interface ModSource {
    Path download(ServerMod mod, Path destDir);
}
