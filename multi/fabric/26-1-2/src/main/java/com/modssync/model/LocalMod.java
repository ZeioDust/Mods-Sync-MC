package com.modssync.model;

import java.nio.file.Path;

/**
 * Represents a mod jar that exists on the local disk inside the player's mods folder.
 *
 * <p>Fabric uses a simple convention for "soft disabling" mods without deleting them:
 * the jar is renamed to {@code modname.jar.disabled}. This record captures that state
 * via the {@code enabled} flag so the rest of the system can decide whether to
 * re-enable (rename back) or leave the file alone without touching the filesystem again.
 *
 * <p>{@code file} is the actual {@link Path} to the jar (or .jar.disabled), which
 * {@link com.modssync.core.FolderMutator} uses when performing rename/delete operations.
 */
public record LocalMod(String modId, String version, Path file, boolean enabled) {}
