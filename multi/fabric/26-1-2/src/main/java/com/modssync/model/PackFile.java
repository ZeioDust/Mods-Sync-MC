package com.modssync.model;

/**
 * Describes a single resourcepack or shaderpack file that the server advertises.
 *
 * <p>{@code folder} is one of the values defined in {@link com.modssync.core.SyncFolders}
 * (e.g. "resourcepacks"), and {@code fileName} is the bare file name with no path
 * separators — validated by {@link com.modssync.core.SyncFolders#isSafeName} before use
 * to prevent path-traversal attacks.
 *
 * <p>{@code size} is used as a lightweight change-detection signal: if the local file
 * already exists and has the same byte count, it's considered up to date and skipped.
 * A full hash comparison isn't done here because pack files can be large and size
 * alone is usually sufficient for the additive-sync use case.
 */
public record PackFile(String folder, String fileName, long size) {}
