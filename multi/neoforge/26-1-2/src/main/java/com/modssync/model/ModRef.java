package com.modssync.model;

/** A mod identified by id + version string. Versions compared by exact equality. */
public record ModRef(String modId, String version) {}
