package com.modssync.model;

import java.nio.file.Path;

/** A mod jar found in the local mods folder. {@code enabled} is false for *.jar.disabled. */
public record LocalMod(String modId, String version, Path file, boolean enabled) {}
